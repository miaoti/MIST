package io.mist.cli.auth;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure-config coverage for the PER_JVM_COOKIE mode (G3 SUT-2 engineering item iii).
 * The live cookie capture is exercised against the deployed Sock Shop; here we pin
 * the mode parsing, the ${unique} username resolution, and the header-mode contracts
 * that generated tests rely on (no bearer token; 401-refresh disabled).
 */
public class MstAuthHandlerCookieModeTest {

    @After
    public void reset() {
        System.clearProperty("auth.mode");
        System.clearProperty("auth.login.username");
        MstAuthHandler.reload();
    }

    @Test
    public void cookieMode_parsesAndDisablesHeaderCentricMachinery() {
        System.setProperty("auth.mode", "per_jvm_cookie");
        MstAuthHandler.reload();
        assertEquals(MstAuthHandler.Mode.PER_JVM_COOKIE, MstAuthHandler.getMode());
        // no bearer token exists in cookie mode (callers pass it as an optional)
        assertNull(MstAuthHandler.getDefaultToken());
        // the 401-refresh filter re-stamps the Authorization HEADER — off in cookie mode
        assertFalse(MstAuthHandler.isRefreshOn401Enabled());
    }

    @Test
    public void uniqueToken_resolvesToAStableRegistrableUsername() {
        System.setProperty("auth.mode", "per_jvm_cookie");
        System.setProperty("auth.login.username", "mist${unique}");
        MstAuthHandler.reload();
        String first = MstAuthHandler.resolvedLoginUsername();
        String second = MstAuthHandler.resolvedLoginUsername();
        assertEquals("per-JVM suffix must be stable across calls", first, second);
        assertTrue("suffix must be substituted", first.startsWith("mist"));
        assertFalse("no unresolved token may remain", first.contains("${unique}"));
    }

    @Test
    public void plainUsername_isUntouched() {
        System.setProperty("auth.mode", "per_jvm_cookie");
        System.setProperty("auth.login.username", "fdse_microservice");
        MstAuthHandler.reload();
        assertEquals("fdse_microservice", MstAuthHandler.resolvedLoginUsername());
    }
}
