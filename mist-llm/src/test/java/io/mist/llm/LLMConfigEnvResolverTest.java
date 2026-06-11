package io.mist.llm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Locks in the env-var placeholder resolver used for the optional
 * {@code llm.local.api.key} property. The resolver lets the secret stay
 * outside the committed properties file (read from {@code $ENV_VAR} or
 * {@code -DSYS_PROP}).
 */
public class LLMConfigEnvResolverTest {

    @Test
    public void literalValuePassesThrough() {
        assertEquals("sk-abc", LLMConfig.resolveEnvPlaceholder("sk-abc"));
    }

    @Test
    public void emptyAndNullReturnEmpty() {
        assertEquals("", LLMConfig.resolveEnvPlaceholder(""));
        assertEquals("", LLMConfig.resolveEnvPlaceholder(null));
    }

    @Test
    public void placeholderWithoutMatchingEnvReturnsEmpty() {
        assertEquals("", LLMConfig.resolveEnvPlaceholder("${RESTEST_NO_SUCH_VAR_XYZ}"));
    }

    @Test
    public void placeholderWithDefaultUsesDefault() {
        assertEquals("fallback",
                LLMConfig.resolveEnvPlaceholder("${RESTEST_NO_SUCH_VAR_XYZ:fallback}"));
    }

    @Test
    public void systemPropertyFallback() {
        String key = "RESTEST_LLMCONFIG_TEST_KEY";
        try {
            System.setProperty(key, "sk-from-sys");
            assertEquals("sk-from-sys",
                    LLMConfig.resolveEnvPlaceholder("${" + key + "}"));
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    public void surroundingWhitespaceIsTrimmed() {
        assertEquals("sk-abc", LLMConfig.resolveEnvPlaceholder("  sk-abc  "));
    }

    /**
     * The .api_keys/{VAR} fallback exists so that IDE env-var injection
     * failures don't break the LLM config. The resolver reads relative to
     * the JVM CWD, which we can't easily change at runtime, so we write a
     * fixture into the actual project's .api_keys/ directory under a unique
     * varname and clean up after. The directory itself is gitignored.
     */
    @Test
    public void fileFallbackResolves() throws Exception {
        String key = "RESTEST_FILE_FALLBACK_UNIT_TEST_" + System.nanoTime();
        System.clearProperty(key);
        java.nio.file.Path keyFile = java.nio.file.Paths.get(".api_keys", key);
        java.nio.file.Files.createDirectories(keyFile.getParent());
        java.nio.file.Files.write(keyFile, "sk-from-file\n".getBytes());
        try {
            assertEquals("sk-from-file",
                    LLMConfig.resolveEnvPlaceholder("${" + key + "}"));
        } finally {
            java.nio.file.Files.deleteIfExists(keyFile);
        }
    }
}
