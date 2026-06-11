package io.mist.core.config;

/**
 * Master cache read/write trigger shared by every signature-based LLM cache
 * in MIST. Operators set the trigger ONCE — via {@code .properties} file or
 * {@code -D} system property — and it controls all caches uniformly.
 *
 * <p>Two flags:
 * <ul>
 *   <li>{@link #MASTER_READ} — when false, caches NEVER serve a cached
 *       response; every request flows to the underlying LLM call.</li>
 *   <li>{@link #MASTER_WRITE} — when false, fresh LLM responses are NOT
 *       persisted back to the cache file.</li>
 * </ul>
 *
 * <p>Useful combinations:
 * <ul>
 *   <li>{@code read=true write=true} (default): full caching</li>
 *   <li>{@code read=true write=false}: read-only playback — pin existing
 *       cache contents, ignore any new LLM responses</li>
 *   <li>{@code read=false write=true}: refresh — every call hits LLM and
 *       overwrites the prior cached value; subsequent runs see the new</li>
 *   <li>{@code read=false write=false}: bypass entirely</li>
 * </ul>
 *
 * <p>This master trigger is the single source of truth for the three caches
 * added for paper/A会 reproducibility (status-code discovery, exploration
 * suggest, LLM validation). Other caches (LLMCallCache,
 * EnhancementCache, IntelligentAnalysisCache, ParameterErrorAnalysisCache,
 * SoftErrorRuleCache) currently keep their own per-cache toggles for
 * backwards compatibility; retrofit is tracked separately.
 */
public final class CacheToggle {

    /** System property name for the master read switch. */
    public static final String MASTER_READ = "mst.cache.read";
    /** System property name for the master write switch. */
    public static final String MASTER_WRITE = "mst.cache.write";

    private CacheToggle() { /* utility */ }

    /** True iff caches should consult their backing store on lookup. */
    public static boolean canRead() {
        return Boolean.parseBoolean(System.getProperty(MASTER_READ, "true"));
    }

    /** True iff caches should persist fresh LLM responses. */
    public static boolean canWrite() {
        return Boolean.parseBoolean(System.getProperty(MASTER_WRITE, "true"));
    }
}
