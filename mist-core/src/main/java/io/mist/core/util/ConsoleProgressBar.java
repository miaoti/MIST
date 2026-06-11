package io.mist.core.util;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Single-line, in-place progress bar for the MIST pipeline.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Global pipeline view.</b> A single bar shows overall progress across
 *       Pool Gen -> Faulty Pools -> Variants -> Writing -> Enhancement.</li>
 *   <li><b>Nested phase stack.</b> Inner phases (e.g. "Pool Params" inside
 *       "Pool Gen") attach as sub-progress; the outer phase keeps contributing
 *       weight to the overall percentage.</li>
 *   <li><b>Compact format.</b> One line, ~90 chars typical — fits the
 *       narrowest practical terminal (IntelliJ default) without wrapping.</li>
 *   <li><b>Self-overwrite via {@code \r + ESC[2K}.</b> Each redraw clears the
 *       current line atomically before drawing, so leftover characters from a
 *       longer previous bar never leak through.</li>
 * </ul>
 *
 * <p>The bar writes only on explicit {@code begin/update/complete} calls. It
 * does <b>not</b> install a System.out wrapper — that is the LoggerStream
 * layer's job (installed by {@code MistRunner.run()}), which silences all
 * raw {@code System.out.println} prints to console while still capturing
 * them in the log file. Without the silencer, sticky-bottom redraws spam
 * the terminal; with the silencer, no one writes to the cursor's line
 * between bar renders, so the bar stays put without needing a wrapper.
 *
 * <h2>Public API</h2>
 * <pre>{@code
 *   ConsoleProgressBar.begin("Pool Gen", 10);
 *   ConsoleProgressBar.update("GET /stations");
 *   ConsoleProgressBar.complete();
 * }</pre>
 *
 * <h2>Auto-disable</h2>
 * <ul>
 *   <li>{@code -Drestest.progress.bar=false} -> bar disabled</li>
 *   <li>{@code CI} env var set -> bar disabled</li>
 *   <li>{@code NO_COLOR} env or {@code -Drestest.progress.bar.color=false} -> colors disabled (bar still renders)</li>
 * </ul>
 */
public final class ConsoleProgressBar {

    // -----------------------------------------------------------------------
    //  Phase catalog (weights sum to 100)
    // -----------------------------------------------------------------------

    public enum Phase {
        POOL_GEN      ("Pool Gen",       3),
        FAULTY_POOLS  ("Faulty Pools",   7),
        VARIANT_GEN   ("Variants",       5),
        WRITING       ("Writing",        2),
        ENHANCE_ROUNDS("Enhance",       83);

        final String label;
        final int weight;

        Phase(String label, int weight) { this.label = label; this.weight = weight; }

        /** Resolve a phase-name string (any of the historical labels). */
        static Phase fromLabel(String s) {
            if (s == null) return null;
            String n = s.trim();
            for (Phase p : values()) if (p.label.equalsIgnoreCase(n)) return p;
            // Legacy aliases — call sites still pass these old strings.
            if (n.equalsIgnoreCase("Variant Gen"))    return VARIANT_GEN;
            if (n.equalsIgnoreCase("Writing Tests"))  return WRITING;
            if (n.equalsIgnoreCase("Enhance Rounds")) return ENHANCE_ROUNDS;
            return null;
        }
    }

    // -----------------------------------------------------------------------
    //  Visual constants
    // -----------------------------------------------------------------------

    private static final Object LOCK = new Object();
    private static final int BAR_WIDTH = 28;

    private static final String FILLED_BLOCK = "█";

    /** Sub-block characters for fractional fill (1/8-cell resolution). */
    private static final String[] SUB_BLOCKS = {
        "",         // 0/8
        "▏",   // 1/8
        "▎",   // 2/8
        "▍",   // 3/8
        "▌",   // 4/8
        "▋",   // 5/8
        "▊",   // 6/8
        "▉",   // 7/8
    };

    /** Braille spinner rotates one frame per render. */
    private static final String[] SPINNER_FRAMES = {
        "⠋", "⠙", "⠹", "⠸",
        "⠼", "⠴", "⠦", "⠧",
        "⠇", "⠏"
    };
    private static int spinnerIdx = 0;

    private static final String ESC                = "[";   // CSI: ESC + '['
    private static final String ANSI_CLEAR_LINE    = ESC + "2K";
    private static final String ANSI_CR            = "\r";
    private static final String ANSI_RESET         = ESC + "0m";
    private static final String ANSI_BOLD          = ESC + "1m";
    private static final String ANSI_DIM           = ESC + "2m";
    private static final String ANSI_BRIGHT_CYAN   = ESC + "96m";
    private static final String ANSI_BRIGHT_GREEN  = ESC + "92m";
    private static final String ANSI_YELLOW        = ESC + "33m";
    private static final String ANSI_BRIGHT_WHITE  = ESC + "97m";
    private static final String ANSI_GRAY          = ESC + "90m";

    private static final boolean enabled  = detectEnabled();
    private static final boolean useColor = detectColor();

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    /** Raw FileDescriptor stream — bypasses any System.out wrappers. */
    private static final PrintStream RAW_STDOUT =
            new PrintStream(new FileOutputStream(FileDescriptor.out), true);

    /** UTF-8 raw stream — like {@link #RAW_STDOUT} but explicitly UTF-8 so emoji /
     *  box glyphs don't mojibake under a non-UTF-8 default charset. */
    private static final PrintStream RAW_STDOUT_UTF8 =
            new PrintStream(new FileOutputStream(FileDescriptor.out), true,
                            java.nio.charset.StandardCharsets.UTF_8);

    /**
     * Print a block directly to the terminal, bypassing the log4j {@code WARN+}
     * console filter (so an end-of-run summary reaches a user who only reads
     * stdout — a plain {@code System.out.println} is re-routed to the INFO logger
     * and dropped by the console appender). UTF-8 encoded.
     */
    public static void printRaw(String s) {
        RAW_STDOUT_UTF8.println(s);
        RAW_STDOUT_UTF8.flush();
    }

    private static final Deque<Frame> STACK = new ArrayDeque<>();
    private static final Map<Phase, Double> completedFraction = new EnumMap<>(Phase.class);

    private static long pipelineStartNanos = 0;
    /** Monotonic clamp so the bar never regresses across phase transitions. */
    private static double lastOverallFraction = 0.0;

    private ConsoleProgressBar() {}

    private static final class Frame {
        final String label;
        final Phase phase;
        int total;
        int current;
        String itemName = "";
        final long startNanos = System.nanoTime();

        Frame(String label, int total) {
            this.label = label;
            this.total = Math.max(total, 0);
            this.phase = Phase.fromLabel(label);
        }
    }

    // -----------------------------------------------------------------------
    //  Detection
    // -----------------------------------------------------------------------

    private static boolean detectEnabled() {
        String prop = System.getProperty("restest.progress.bar");
        if (prop != null) return Boolean.parseBoolean(prop);
        if (System.getenv("CI") != null) return false;
        return true;
    }

    private static boolean detectColor() {
        // Off by default — ANSI support in IDE / packaged-terminal contexts
        // (IntelliJ Run console without ANSI-mode enabled, Eclipse, jenkins
        // log captures, etc.) is inconsistent, and a falsy-rendering terminal
        // prints "[96m" and similar escape literals over the bar, which is
        // worse than no color. Opt in explicitly when running in a known-
        // capable terminal (modern Linux / macOS shells, IntelliJ with ANSI
        // escape support enabled): -Drestest.progress.bar.color=true or set
        // FORCE_COLOR / CLICOLOR_FORCE env vars.
        if (System.getenv("NO_COLOR") != null) return false;
        String prop = System.getProperty("restest.progress.bar.color");
        if (prop != null) return Boolean.parseBoolean(prop);
        if (System.getenv("FORCE_COLOR") != null) return true;
        if (System.getenv("CLICOLOR_FORCE") != null) return true;
        return false;
    }

    private static String color(String code, String s) {
        return useColor ? code + s + ANSI_RESET : s;
    }

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    public static void begin(String phase, int totalItems) {
        if (!enabled) return;
        synchronized (LOCK) {
            if (pipelineStartNanos == 0) pipelineStartNanos = System.nanoTime();
            Frame f = new Frame(phase, totalItems);
            STACK.push(f);
            // Announce the step on its own line when this is a top-level
            // pipeline phase (so the user sees "what step are we on now?"
            // without having to decode the progress-bar label). Inner phases
            // re-use the current bar line — no extra announcement.
            if (f.phase != null) {
                int phaseNumber = f.phase.ordinal() + 1;
                int totalPhases = Phase.values().length;
                String line = "▶ Step " + phaseNumber + "/" + totalPhases
                        + ": " + f.phase.label;
                if (totalItems > 0) line += " (" + totalItems + " items)";
                RAW_STDOUT.println(line);
                RAW_STDOUT.flush();
            }
            render();
        }
    }

    /**
     * Print a one-time startup banner to RAW_STDOUT. Bypasses log4j so the
     * banner appears even with the console appender filtered to WARN+.
     */
    public static void banner(String version) {
        if (!enabled) return;
        synchronized (LOCK) {
            // Block-letter MIST logo with a one-line tagline. Drawn with the
            // same Unicode box-drawing chars the bar already uses, so no new
            // dependencies on terminal capabilities.
            String[] lines = new String[] {
                "",
                "  ╔════════════════════════════════════════════════════════════╗",
                "  ║                                                            ║",
                "  ║     ███╗   ███╗██╗███████╗████████╗                        ║",
                "  ║     ████╗ ████║██║██╔════╝╚══██╔══╝                        ║",
                "  ║     ██╔████╔██║██║███████╗   ██║                           ║",
                "  ║     ██║╚██╔╝██║██║╚════██║   ██║                           ║",
                "  ║     ██║ ╚═╝ ██║██║███████║   ██║                           ║",
                "  ║     ╚═╝     ╚═╝╚═╝╚══════╝   ╚═╝                           ║",
                "  ║                                                            ║",
                "  ║     Microservice Integration & Scenario Tester             ║",
                "  ║     " + padTo(version == null ? "" : version, 54) + "║",
                "  ║                                                            ║",
                "  ╚════════════════════════════════════════════════════════════╝",
                "",
            };
            for (String l : lines) RAW_STDOUT.println(l);
            RAW_STDOUT.flush();
        }
    }

    private static String padTo(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        StringBuilder b = new StringBuilder(s);
        while (b.length() < width) b.append(' ');
        return b.toString();
    }

    public static void update(String itemName) {
        if (!enabled) return;
        synchronized (LOCK) {
            Frame top = STACK.peek();
            if (top == null) return;
            top.current = Math.min(top.current + 1, top.total);
            top.itemName = itemName != null ? itemName : "";
            render();
        }
    }

    public static void update(int cur, int tot, String itemName) {
        if (!enabled) return;
        synchronized (LOCK) {
            Frame top = STACK.peek();
            if (top == null) return;
            top.current = Math.min(cur, tot);
            top.total   = tot;
            top.itemName = itemName != null ? itemName : "";
            render();
        }
    }

    public static void complete() {
        if (!enabled) return;
        synchronized (LOCK) {
            Frame finished = STACK.poll();
            if (finished == null) return;
            if (finished.phase != null) completedFraction.put(finished.phase, 1.0);
            if (STACK.isEmpty()) {
                render();
                RAW_STDOUT.println();
                RAW_STDOUT.flush();
            } else {
                render();
            }
        }
    }

    public static boolean isActive() {
        synchronized (LOCK) { return !STACK.isEmpty(); }
    }

    /** Package-private inspection hook for the visual contract test. */
    static String currentBarString() {
        synchronized (LOCK) {
            if (STACK.isEmpty()) return "";
            return buildBar();
        }
    }

    /**
     * Package-private reset hook for tests. Drains every piece of static
     * state so consecutive tests see a fresh bar — without this, the
     * {@link #completedFraction} map credited by {@link #complete()} would
     * leak into the next test and cause its outer-phase detection to
     * believe the phase had already finished.
     */
    static void resetForTesting() {
        synchronized (LOCK) {
            STACK.clear();
            completedFraction.clear();
            pipelineStartNanos = 0;
            lastOverallFraction = 0.0;
            spinnerIdx = 0;
        }
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    /** Fallback overwrite width when ANSI line-clear is unavailable. */
    private static final int NO_ANSI_PAD_WIDTH = 120;

    private static void render() {
        if (STACK.isEmpty()) return;
        spinnerIdx = (spinnerIdx + 1) % SPINNER_FRAMES.length;
        String bar = buildBar();
        if (useColor) {
            // ANSI-capable terminal: clear the current line atomically,
            // then draw the bar. Colored bars contain invisible escape
            // codes that would confuse any fixed-width pad, so line-clear
            // is the only reliable cleanup.
            RAW_STDOUT.print(ANSI_CR + ANSI_CLEAR_LINE);
            RAW_STDOUT.print(bar);
        } else {
            // No-ANSI fallback: carriage return rewinds cursor, then the
            // bar overwrites the prefix, then trailing spaces overwrite
            // anything left over from a longer previous bar. No escape
            // codes are emitted — purely Unicode + ASCII so terminals
            // that print "[2K" as literal text don't show garbage.
            RAW_STDOUT.print(ANSI_CR);
            RAW_STDOUT.print(bar);
            int padding = NO_ANSI_PAD_WIDTH - bar.length();
            for (int i = 0; i < padding; i++) RAW_STDOUT.print(' ');
            RAW_STDOUT.print(ANSI_CR);
            RAW_STDOUT.print(bar);
        }
        RAW_STDOUT.flush();
    }

    private static String buildBar() {
        double overall = computeOverallFraction();
        int overallPct = (int) Math.round(overall * 100.0);

        // Fractional fill at 1/8-cell resolution.
        double exactFilled = overall * BAR_WIDTH;
        int wholeBlocks = (int) Math.floor(exactFilled);
        int subBlockIdx = (int) Math.round((exactFilled - wholeBlocks) * 8);
        if (subBlockIdx >= SUB_BLOCKS.length) {
            wholeBlocks++;
            subBlockIdx = 0;
        }
        if (wholeBlocks > BAR_WIDTH) wholeBlocks = BAR_WIDTH;
        int emptyCells = BAR_WIDTH - wholeBlocks - (subBlockIdx > 0 ? 1 : 0);
        if (emptyCells < 0) emptyCells = 0;

        StringBuilder filledPart = new StringBuilder(BAR_WIDTH);
        for (int i = 0; i < wholeBlocks; i++) filledPart.append(FILLED_BLOCK);
        if (subBlockIdx > 0) filledPart.append(SUB_BLOCKS[subBlockIdx]);
        StringBuilder emptyPart = new StringBuilder(BAR_WIDTH);
        for (int i = 0; i < emptyCells; i++) emptyPart.append(' ');

        // Locate outermost recognized Phase + innermost frame.
        Frame outerRecognized = null;
        Frame innermost = null;
        for (Frame f : STACK) {
            if (innermost == null) innermost = f;
            if (f.phase != null && !completedFraction.containsKey(f.phase)) {
                outerRecognized = f;
            }
        }

        long elapsedNs = System.nanoTime() - pipelineStartNanos;
        String elapsed = formatDurationCompact(elapsedNs);
        String eta = computeEtaCompact(elapsedNs, overall);

        StringBuilder bar = new StringBuilder(160);

        // Spinner
        bar.append(color(ANSI_BRIGHT_CYAN, SPINNER_FRAMES[spinnerIdx])).append(' ');

        // [ fill / empty ]
        bar.append(color(ANSI_GRAY, "["));
        bar.append(color(ANSI_BRIGHT_GREEN, filledPart.toString()));
        bar.append(color(ANSI_DIM,         emptyPart.toString()));
        bar.append(color(ANSI_GRAY, "]"));

        // Percent
        bar.append(' ').append(color(ANSI_BOLD + ANSI_YELLOW,
                String.format(Locale.ROOT, "%3d%%", overallPct)));

        // Phase + counter — short form, no "Phase N/M" prefix, no labels.
        if (outerRecognized != null) {
            bar.append("  ").append(color(ANSI_BRIGHT_CYAN, outerRecognized.phase.label));
            if (outerRecognized.total > 0) {
                bar.append(' ').append(color(ANSI_BRIGHT_WHITE,
                        outerRecognized.current + "/" + outerRecognized.total));
            }
        }

        // Inner counter only — drop the label and the item name (those belong
        // in the log file, not the progress bar).
        if (innermost != null && innermost != outerRecognized && innermost.total > 0) {
            bar.append(' ').append(color(ANSI_DIM,
                    "(" + innermost.current + "/" + innermost.total + ")"));
        }

        // Elapsed + ETA — no labels, separator dot.
        bar.append(color(ANSI_GRAY, "  · "));
        bar.append(color(ANSI_BRIGHT_WHITE, elapsed));
        if (eta != null) {
            bar.append(color(ANSI_GRAY, " → "));
            bar.append(color(ANSI_BRIGHT_WHITE, eta));
        }

        return bar.toString();
    }

    /**
     * Overall pipeline fraction normalized against the fixed 100-unit weight
     * budget of {@link Phase}. Monotonic clamp prevents regressions across
     * inner-phase transitions.
     */
    private static double computeOverallFraction() {
        double accumulated = 0;
        for (Map.Entry<Phase, Double> e : completedFraction.entrySet()) {
            accumulated += e.getKey().weight * e.getValue();
        }

        Frame outerRecognized = null;
        Frame innermost = null;
        for (Frame f : STACK) {
            if (innermost == null) innermost = f;
            if (f.phase != null && !completedFraction.containsKey(f.phase)) {
                outerRecognized = f;
            }
        }

        if (outerRecognized != null) {
            int outerTotal = Math.max(outerRecognized.total, 1);
            double outerFrac = ((double) outerRecognized.current) / outerTotal;
            if (innermost != null && innermost != outerRecognized && innermost.total > 0) {
                double innerFrac = ((double) innermost.current) / innermost.total;
                outerFrac += innerFrac / outerTotal;
            }
            if (outerFrac > 1.0) outerFrac = 1.0;
            accumulated += outerRecognized.phase.weight * outerFrac;
        }

        double overall = Math.max(0.0, Math.min(1.0, accumulated / 100.0));
        if (overall < lastOverallFraction) overall = lastOverallFraction;
        lastOverallFraction = overall;
        return overall;
    }

    private static String computeEtaCompact(long elapsedNs, double overall) {
        if (overall <= 0.02) return null;
        long totalEstimateNs = (long) (elapsedNs / overall);
        long remainingNs = totalEstimateNs - elapsedNs;
        if (remainingNs <= 0) return null;
        return formatDurationCompact(remainingNs);
    }

    /**
     * Colon-separated compact format: "2:45", "1:23:45", "12s" for sub-minute.
     * Shorter than the previous "1h 23m 45s" so the bar fits one terminal line.
     */
    private static String formatDurationCompact(long nanos) {
        long totalSeconds = TimeUnit.NANOSECONDS.toSeconds(nanos);
        long hours = totalSeconds / 3600;
        long mins  = (totalSeconds % 3600) / 60;
        long secs  = totalSeconds % 60;
        if (hours > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", hours, mins, secs);
        if (mins > 0)  return String.format(Locale.ROOT, "%d:%02d", mins, secs);
        return secs + "s";
    }
}
