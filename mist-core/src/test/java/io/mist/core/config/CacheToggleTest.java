package io.mist.core.config;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the four states of the master cache trigger
 * ({@link CacheToggle#MASTER_READ} × {@link CacheToggle#MASTER_WRITE}).
 *
 * <p>The point of this test isn't to cover the trivial Boolean.parseBoolean
 * code — it's to <em>document</em> in code that the four-state matrix
 * (read=t/f × write=t/f) is the contract every signature-based cache must
 * honor. If a future change accidentally re-introduces a per-cache flag, the
 * cache documentation will diverge from this test's expectations, and the
 * mismatch becomes easy to spot.
 */
public class CacheToggleTest {

    private String prevRead;
    private String prevWrite;

    @Before
    public void setUp() {
        prevRead = System.getProperty(CacheToggle.MASTER_READ);
        prevWrite = System.getProperty(CacheToggle.MASTER_WRITE);
        System.clearProperty(CacheToggle.MASTER_READ);
        System.clearProperty(CacheToggle.MASTER_WRITE);
    }

    @After
    public void tearDown() {
        if (prevRead == null) System.clearProperty(CacheToggle.MASTER_READ);
        else System.setProperty(CacheToggle.MASTER_READ, prevRead);
        if (prevWrite == null) System.clearProperty(CacheToggle.MASTER_WRITE);
        else System.setProperty(CacheToggle.MASTER_WRITE, prevWrite);
    }

    @Test
    public void defaults_areReadOnAndWriteOn() {
        assertTrue(CacheToggle.canRead());
        assertTrue(CacheToggle.canWrite());
    }

    @Test
    public void readOnly_writeOff_pinsCacheContents() {
        System.setProperty(CacheToggle.MASTER_READ, "true");
        System.setProperty(CacheToggle.MASTER_WRITE, "false");
        assertTrue(CacheToggle.canRead());
        assertFalse(CacheToggle.canWrite());
    }

    @Test
    public void refresh_readOff_writeOn_alwaysCallsLlmAndOverwrites() {
        System.setProperty(CacheToggle.MASTER_READ, "false");
        System.setProperty(CacheToggle.MASTER_WRITE, "true");
        assertFalse(CacheToggle.canRead());
        assertTrue(CacheToggle.canWrite());
    }

    @Test
    public void bypass_bothOff_caches_inert() {
        System.setProperty(CacheToggle.MASTER_READ, "false");
        System.setProperty(CacheToggle.MASTER_WRITE, "false");
        assertFalse(CacheToggle.canRead());
        assertFalse(CacheToggle.canWrite());
    }
}
