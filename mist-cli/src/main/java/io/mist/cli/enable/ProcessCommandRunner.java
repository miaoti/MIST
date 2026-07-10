package io.mist.cli.enable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Production {@link SqlDurableReadback.CommandRunner}: runs argv via {@link ProcessBuilder} with a
 * hard timeout, capturing stdout and stderr separately. A timeout destroys the process and surfaces
 * as an exception (so {@link SqlDurableReadback} maps it to a non-2xx UNUSABLE read, never absence).
 * Exercised end-to-end at run time against {@code kubectl exec ... psql}; the transport's parsing
 * and error-vs-absence logic is unit-tested through the injected fake runner.
 */
public final class ProcessCommandRunner implements SqlDurableReadback.CommandRunner {

    @Override
    public Result run(List<String> argv, int timeoutMs) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.redirectErrorStream(false);
        Process p = pb.start();
        // Drain both streams concurrently so a full stderr pipe cannot deadlock stdout.
        StreamPump out = new StreamPump(p.getInputStream());
        StreamPump err = new StreamPump(p.getErrorStream());
        Thread tOut = new Thread(out, "sqlreadback-stdout");
        Thread tErr = new Thread(err, "sqlreadback-stderr");
        tOut.start();
        tErr.start();
        boolean finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            p.destroyForcibly();
            tOut.join(1000);
            tErr.join(1000);
            throw new IllegalStateException("command timed out after " + timeoutMs + "ms: " + argv.get(0));
        }
        tOut.join(1000);
        tErr.join(1000);
        return new Result(p.exitValue(), out.text(), err.text());
    }

    /** Reads a stream to a UTF-8 string on its own thread. */
    private static final class StreamPump implements Runnable {
        private final InputStream in;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private volatile Exception error;

        StreamPump(InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            byte[] chunk = new byte[4096];
            try {
                int n;
                while ((n = in.read(chunk)) != -1) {
                    buf.write(chunk, 0, n);
                }
            } catch (Exception e) {
                error = e;
            }
        }

        String text() {
            return buf.toString(StandardCharsets.UTF_8);
        }
    }
}
