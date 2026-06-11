package io.mist.core.util;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

/**
 * Log4j2 filter that suppresses byte-identical message bodies on the
 * appender it's attached to when the same message has already been
 * emitted within {@link #ttlMillis}. The file appender (no filter)
 * keeps every event so the audit trail is intact; only the console
 * stops repeating itself.
 *
 * <p>Motivation: smart-fetch retries fire 3-5x against a 5xx endpoint
 * in quick succession, and the parameter validator rejects the same
 * value at every location it appears, producing identical WARN lines
 * the user has to scroll past. The file still records every retry.
 *
 * <p>Configured in {@code log4j2.properties} via the {@code consoleDedup}
 * filter type. TTL is overridable via {@code -Dmst.console.dedup.ttl.ms}.
 */
@Plugin(name = "ConsoleDedupFilter", category = Node.CATEGORY,
        elementType = Filter.ELEMENT_TYPE, printObject = true)
public final class ConsoleDedupFilter extends AbstractFilter {

    private static final int MAX_CACHE_ENTRIES = 256;
    private static final long DEFAULT_TTL_MS = 5_000L;

    private final long ttlMillis;
    private final Map<String, Long> recent;

    private ConsoleDedupFilter(long ttlMillis) {
        super(Result.NEUTRAL, Result.DENY);
        this.ttlMillis = ttlMillis;
        this.recent = new LinkedHashMap<String, Long>(MAX_CACHE_ENTRIES, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > MAX_CACHE_ENTRIES;
            }
        };
    }

    @PluginFactory
    public static ConsoleDedupFilter createFilter(
            @PluginAttribute(value = "ttlMillis", defaultLong = DEFAULT_TTL_MS) long ttlMillis,
            // Log4j2's PropertiesConfigurationBuilder always sets onMatch/onMismatch
            // on a Filter element. Without these declared we get a startup ERROR
            // ("ConsoleDedupFilter contains invalid attributes 'onMatch', 'onMismatch'")
            // and the filter is silently dropped. Accepted but ignored — the result
            // codes are fixed (NEUTRAL on miss, DENY on dedupe hit) in the constructor
            // because that's the only sane behavior for this filter; letting operators
            // override would defeat the point.
            @PluginAttribute(value = "onMatch") String onMatchIgnored,
            @PluginAttribute(value = "onMismatch") String onMismatchIgnored) {
        long sysOverride = Long.getLong("mst.console.dedup.ttl.ms", -1L);
        return new ConsoleDedupFilter(sysOverride > 0 ? sysOverride : ttlMillis);
    }

    @Override
    public Result filter(LogEvent event) {
        if (event == null) return Result.NEUTRAL;
        Message m = event.getMessage();
        if (m == null) return Result.NEUTRAL;
        return decide(m.getFormattedMessage(), System.currentTimeMillis());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        if (msg == null) return Result.NEUTRAL;
        return decide(msg.getFormattedMessage(), System.currentTimeMillis());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        return decide(String.valueOf(msg), System.currentTimeMillis());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        return decide(msg, System.currentTimeMillis());
    }

    /**
     * Synchronized so concurrent appender threads (parallel test execution)
     * see each other's recent-message timestamps and dedupe across threads,
     * not just within a single thread.
     */
    private synchronized Result decide(String message, long now) {
        if (message == null) return Result.NEUTRAL;
        Long lastSeen = recent.get(message);
        if (lastSeen != null && (now - lastSeen) < ttlMillis) {
            return Result.DENY;
        }
        recent.put(message, now);
        return Result.NEUTRAL;
    }
}
