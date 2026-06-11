package io.mist.core.util;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

/**
 * {@link OutputStream} adapter that pipes writes to a log4j {@link Logger}
 * (always) and optionally mirrors them to a backing {@link OutputStream}.
 *
 * <p>The mirror was historically always on, which doubled every
 * {@code System.out.println} on the console: once as a raw byte write
 * and again as a log4j-formatted line through the console appender.
 * The {@code mirrorToStream=false} constructor turns the mirror off so
 * the console only gets the log4j-filtered view (currently WARN+ via
 * {@code log4j2.properties}) while everything below that threshold
 * still lands in the file appender at INFO+. The {@link ConsoleProgressBar}
 * writes through a raw {@code FileDescriptor.out} stream, so its output
 * is unaffected by either setting.
 */
public class LoggerStream extends OutputStream
{
    private final Logger logger;
    private final Level logLevel;
    private final OutputStream outputStream;
    private final boolean mirrorToStream;

    /**
     * Legacy constructor — mirrors every write to {@code outputStream}
     * in addition to logging. Kept for callers that explicitly want the
     * dual destination.
     */
    public LoggerStream(Logger logger, Level logLevel, OutputStream outputStream)
    {
        this(logger, logLevel, outputStream, true);
    }

    /**
     * @param mirrorToStream when {@code false}, writes go to the logger
     *                       only and {@code outputStream} is never
     *                       touched — use this to silence the raw
     *                       console echo and let log4j thresholds
     *                       decide what reaches the terminal.
     */
    public LoggerStream(Logger logger, Level logLevel, OutputStream outputStream, boolean mirrorToStream)
    {
        super();

        this.logger = logger;
        this.logLevel = logLevel;
        this.outputStream = outputStream;
        this.mirrorToStream = mirrorToStream;
    }

    @Override
    public void write(byte[] b) throws IOException
    {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException
    {
        if (mirrorToStream) outputStream.write(b, off, len);
        String string = new String(b, off, len);
        if (!string.trim().isEmpty())
            logger.log(logLevel, string);
    }

    @Override
    public void write(int b) throws IOException
    {
        if (mirrorToStream) outputStream.write(b);
        String string = String.valueOf((char) b);
        if (!string.trim().isEmpty())
            logger.log(logLevel, string);
    }
}
