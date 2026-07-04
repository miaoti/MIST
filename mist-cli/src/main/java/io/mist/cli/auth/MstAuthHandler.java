package io.mist.cli.auth;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Runtime auth helper used by MST-generated test classes.
 *
 * Replaces the previous per-test inline login (one POST /api/v1/users/login per
 * test method, ~2400 hits per run for trainticket) with a configurable strategy:
 *
 * <ul>
 *   <li>{@code none}        - no auth at all; tests run anonymously.</li>
 *   <li>{@code static_token}- use the value of {@code auth.static.token} verbatim;
 *                              no login call ever made.</li>
 *   <li>{@code per_jvm}     - lazy login on first request, token cached in this
 *                              static field for the whole JVM. The whole MST
 *                              suite makes 1-2 login calls instead of N.</li>
 *   <li>{@code per_test}    - legacy behaviour: a fresh login per test method.</li>
 *   <li>{@code per_jvm_cookie} - lazy login on first request, but the SESSION
 *                              COOKIES from the login/register response are cached
 *                              and attached to every request (no Authorization
 *                              header). For cookie-session SUTs like Sock Shop
 *                              (front-end sets {@code logged_in}+{@code md.sid} on
 *                              POST /register; G3 SUT-2 engineering item iii).
 *                              {@code ${unique}} in {@code auth.login.username}
 *                              resolves to a per-JVM suffix so a register-as-login
 *                              never collides across runs. {@code overrideToken}
 *                              manipulations and the 401-refresh filter are
 *                              Authorization-header-centric and do not apply in
 *                              this mode (documented, not silently emulated).</li>
 * </ul>
 *
 * Configuration is read from {@code System.getProperty(...)} (loaded by
 * {@link io.mist.core.config.legacy.MstConfig}, which pushes
 * every key from {@code trainticket-mst.properties} to System properties before
 * the writer or generated tests run).
 *
 * Per-test override (e.g. {@code AuthManipulationStrategy} INVALID_TOKEN /
 * REMOVE_AUTH for status-code exploration) is supported by passing an explicit
 * {@code overrideToken} or {@code disableAuth} flag to
 * {@link #applyAuth(RequestSpecification, String, String, boolean)}.
 *
 * Path-based skipping (C2): paths matching any pattern in
 * {@code auth.skip.path.patterns} (CSV of regex) get no Authorization header
 * regardless of mode. Useful for {@code /actuator/*}, {@code /login}, etc.
 */
public final class MstAuthHandler {

    private static final Logger log = LogManager.getLogger(MstAuthHandler.class);

    public enum Mode { NONE, STATIC_TOKEN, PER_JVM, PER_TEST, PER_JVM_COOKIE }

    private static final Object LOGIN_LOCK = new Object();
    private static volatile String cachedToken;
    /** PER_JVM_COOKIE: the session cookies captured from the login/register response. */
    private static volatile io.restassured.http.Cookies cachedCookies;
    /** Per-JVM suffix for the ${unique} username token (stable across reload()). */
    private static final String UNIQUE_SUFFIX =
            Long.toString(System.currentTimeMillis() % 100_000_000L);
    /** nanos when cachedToken was last set — lets the filter skip relogin if token is fresh. */
    private static volatile long tokenSetNanos = 0L;
    private static volatile boolean configLoaded;

    // Resolved configuration (mutable only via reload())
    private static Mode mode;
    private static String tokenHeader;
    private static String tokenPrefix;
    private static String loginUrl;
    private static String loginUsername;
    private static String loginPassword;
    private static String loginBodyTemplate;
    private static String loginTokenJsonPath;
    private static int loginExpectedStatus;
    private static String staticToken;
    private static boolean refreshOn401;
    private static List<Pattern> skipPatterns;

    private MstAuthHandler() {}

    private static void ensureConfigLoaded() {
        if (configLoaded) return;
        synchronized (LOGIN_LOCK) {
            if (configLoaded) return;
            reload();
            configLoaded = true;
        }
    }

    /** Re-read configuration from System properties. Public for tests. */
    public static synchronized void reload() {
        // Default NONE: do not assume the SUT needs authentication. Previously this
        // defaulted to PER_JVM (a train-ticket-centric assumption), so ANY SUT that
        // doesn't require login — and didn't explicitly set auth.mode=none — had every
        // generated test attempt a login (e.g. /api/v1/users/login → 404 on Bookinfo),
        // fail, and SKIP every request. train-ticket sets auth.mode=per_jvm explicitly
        // in its own properties, so it is unaffected; a no-auth SUT now works with no
        // auth config at all. Unknown/typo'd values also resolve to NONE (fail-open to
        // "no auth" rather than silently attempting a login).
        String modeStr = System.getProperty("auth.mode", "none").trim().toLowerCase();
        switch (modeStr) {
            case "static_token":   mode = Mode.STATIC_TOKEN; break;
            case "per_test":       mode = Mode.PER_TEST; break;
            case "per_jvm":        mode = Mode.PER_JVM; break;
            case "per_jvm_cookie": mode = Mode.PER_JVM_COOKIE; break;
            case "none":
            default:               mode = Mode.NONE; break;
        }
        tokenHeader        = System.getProperty("auth.token.header", "Authorization");
        tokenPrefix        = System.getProperty("auth.token.prefix", "Bearer ");
        loginUrl           = System.getProperty("auth.login.url", "/api/v1/users/login");
        loginUsername      = System.getProperty("auth.login.username", "admin");
        loginPassword      = System.getProperty("auth.login.password", "222222");
        loginBodyTemplate  = System.getProperty("auth.login.body.template",
                "{\"username\":\"${username}\",\"password\":\"${password}\"}");
        loginTokenJsonPath = System.getProperty("auth.login.token.json.path", "data.token");
        loginExpectedStatus = Integer.parseInt(System.getProperty("auth.login.expected.status", "200"));
        staticToken        = System.getProperty("auth.static.token", "");
        refreshOn401       = Boolean.parseBoolean(System.getProperty("auth.refresh.on.401", "true"));

        skipPatterns = new ArrayList<>();
        String csv = System.getProperty("auth.skip.path.patterns", "");
        for (String raw : csv.split(",")) {
            String pat = raw.trim();
            if (pat.isEmpty()) continue;
            try {
                skipPatterns.add(Pattern.compile(pat));
            } catch (Exception e) {
                log.warn("Invalid auth.skip.path.patterns regex '{}': {}", pat, e.getMessage());
            }
        }
        cachedToken = null;
        cachedCookies = null;
    }

    /** The login username with {@code ${unique}} resolved (package-visible for tests). */
    static String resolvedLoginUsername() {
        ensureConfigLoaded();
        return loginUsername == null ? null : loginUsername.replace("${unique}", UNIQUE_SUFFIX);
    }

    /**
     * Make sure auth is "ready" for the current mode. Triggers the lazy login
     * when needed and caches the token. Returns {@code true} when the mode
     * needs no login (NONE / STATIC_TOKEN with non-empty token) or when login
     * succeeded; {@code false} when login failed (caller should treat
     * subsequent requests as auth-less).
     *
     * Safe to call from many test methods - only the first call (per JVM in
     * PER_JVM mode, or every call in PER_TEST mode) actually hits the network.
     */
    public static boolean ensureReady() {
        ensureConfigLoaded();
        switch (mode) {
            case NONE:
                return true;
            case STATIC_TOKEN:
                return staticToken != null && !staticToken.isEmpty();
            case PER_TEST:
                String fresh = login();
                cachedToken = fresh;
                if (fresh != null) tokenSetNanos = System.nanoTime();
                return fresh != null;
            case PER_JVM_COOKIE:
                if (cachedCookies != null) return true;
                synchronized (LOGIN_LOCK) {
                    if (cachedCookies == null) {
                        cachedCookies = loginForCookies();
                    }
                }
                return cachedCookies != null;
            case PER_JVM:
            default:
                if (cachedToken != null) return true;
                synchronized (LOGIN_LOCK) {
                    if (cachedToken == null) {
                        cachedToken = login();
                        if (cachedToken != null) tokenSetNanos = System.nanoTime();
                    }
                }
                return cachedToken != null;
        }
    }

    /** Force the next {@link #ensureReady()} (PER_JVM / PER_JVM_COOKIE) to log in again. */
    public static void invalidate() {
        cachedToken = null;
        cachedCookies = null;
        tokenSetNanos = 0L;
    }

    /** Nanos since the current cachedToken was minted (or Long.MAX_VALUE if none/uncached mode). */
    public static long nanosSinceTokenSet() {
        if (tokenSetNanos == 0L) return Long.MAX_VALUE;
        return System.nanoTime() - tokenSetNanos;
    }

    /**
     * The token used by default (from cache or static config). May be
     * {@code null} when mode == NONE or login has not run / failed.
     */
    public static String getDefaultToken() {
        ensureConfigLoaded();
        if (mode == Mode.NONE) return null;
        if (mode == Mode.STATIC_TOKEN) return staticToken;
        return cachedToken;
    }

    public static Mode getMode() {
        ensureConfigLoaded();
        return mode;
    }

    public static String getTokenHeader() {
        ensureConfigLoaded();
        return tokenHeader;
    }

    public static String getTokenPrefix() {
        ensureConfigLoaded();
        return tokenPrefix;
    }

    /** True when {@code auth.refresh.on.401=true} (default). Consulted by
     *  {@link MstAuthRefreshFilter}; disabled when mode is NONE or
     *  STATIC_TOKEN (no point re-logging-in if there is no login flow) and for
     *  PER_JVM_COOKIE (the filter's retry re-stamps the Authorization HEADER,
     *  which does not exist in cookie mode; session lifetime is expected to
     *  exceed a run — disclosed, not silently emulated). */
    public static boolean isRefreshOn401Enabled() {
        ensureConfigLoaded();
        if (mode == Mode.NONE || mode == Mode.STATIC_TOKEN || mode == Mode.PER_JVM_COOKIE) {
            return false;
        }
        return refreshOn401;
    }

    /** Returns true when this path is opted out of auth via skip patterns. */
    public static boolean isSkipPath(String path) {
        ensureConfigLoaded();
        if (path == null || skipPatterns.isEmpty()) return false;
        for (Pattern p : skipPatterns) {
            if (p.matcher(path).find()) return true;
        }
        return false;
    }

    /**
     * Stamp the Authorization header on {@code req}, honouring:
     * <ol>
     *   <li>{@code disableAuth} - if true (REMOVE_AUTH manipulation), no header.</li>
     *   <li>{@link #isSkipPath(String)} - skip patterns (e.g. /actuator).</li>
     *   <li>{@code overrideToken} - per-test override (e.g. INVALID_TOKEN); when
     *       non-null it is used instead of the cached/static token.</li>
     *   <li>Otherwise the default token from {@link #getDefaultToken()}.</li>
     * </ol>
     * If no token is available (mode=NONE, or login failed), the request is
     * returned unchanged - the API will respond with 401 if it required auth.
     */
    public static RequestSpecification applyAuth(RequestSpecification req,
                                                 String path,
                                                 String overrideToken,
                                                 boolean disableAuth) {
        ensureConfigLoaded();
        if (disableAuth) return req;
        if (isSkipPath(path)) return req;
        if (mode == Mode.PER_JVM_COOKIE) {
            // Cookie-session SUT: attach the cached session cookies; there is no
            // Authorization header in this mode (overrideToken manipulations are
            // header-centric and intentionally not emulated — see class doc).
            io.restassured.http.Cookies cookies = cachedCookies;
            return (cookies == null || cookies.size() == 0) ? req : req.cookies(cookies);
        }
        String token = overrideToken;
        if (token == null) token = getDefaultToken();
        if (token == null || token.isEmpty()) return req;
        return req.header(tokenHeader, joinPrefixAndToken(tokenPrefix, token));
    }

    /**
     * Join the configured prefix and the JWT, defensively normalizing the
     * separator. RFC 6750 requires exactly one space between the auth scheme
     * ("Bearer") and the token. Some property files set
     * {@code auth.token.prefix=Bearer} without a trailing space — naive
     * concatenation produces {@code BearereyJ...}, which the server's
     * {@code JWTFilter} silently rejects (it does {@code startsWith("Bearer ")}),
     * leading to a 403 on every protected endpoint. We strip any whitespace
     * the user happened to put on the prefix and re-insert exactly one space.
     * An empty prefix is passed through unchanged for non-Bearer schemes.
     */
    private static String joinPrefixAndToken(String prefix, String token) {
        if (prefix == null || prefix.isEmpty()) return token;
        return prefix.trim() + " " + token;
    }

    /** Convenience for callers that have no per-test override / disable flag. */
    public static RequestSpecification applyAuth(RequestSpecification req, String path) {
        return applyAuth(req, path, null, false);
    }

    /**
     * Perform the login HTTP call and extract the token. Returns {@code null}
     * if the call fails or the token JSON path resolves to nothing.
     */
    private static String login() {
        try {
            Response res = postLogin();
            String token = res.jsonPath().getString(loginTokenJsonPath);
            if (token == null || token.isEmpty()) {
                log.error("MstAuthHandler: login succeeded but token at jsonPath '{}' was empty",
                        loginTokenJsonPath);
                return null;
            }
            log.info("MstAuthHandler: login OK, token cached (len={})", token.length());
            return token;
        } catch (Throwable t) {
            log.error("MstAuthHandler: login failed - {}: {}",
                    t.getClass().getSimpleName(), t.getMessage());
            return null;
        }
    }

    /**
     * PER_JVM_COOKIE: perform the login/register call and capture the SESSION
     * COOKIES from the response (for Sock Shop: {@code logged_in} + {@code md.sid}
     * set by POST /register). Returns {@code null} when the call fails or sets no
     * cookies — the caller then treats requests as auth-less, same contract as
     * {@link #login()}.
     */
    private static io.restassured.http.Cookies loginForCookies() {
        try {
            Response res = postLogin();
            io.restassured.http.Cookies cookies = res.getDetailedCookies();
            if (cookies == null || cookies.size() == 0) {
                log.error("MstAuthHandler: cookie login returned no Set-Cookie (url={})", loginUrl);
                return null;
            }
            List<String> names = new ArrayList<>();
            cookies.asList().forEach(c -> names.add(c.getName()));
            log.info("MstAuthHandler: cookie login OK, cached session cookies {}", names);
            return cookies;
        } catch (Throwable t) {
            log.error("MstAuthHandler: cookie login failed - {}: {}",
                    t.getClass().getSimpleName(), t.getMessage());
            return null;
        }
    }

    /** Shared login POST: URL composition + ${username}/${password}/${unique} body build. */
    private static Response postLogin() {
        String user = loginUsername.replace("${unique}", UNIQUE_SUFFIX);
        String body = loginBodyTemplate
                .replace("${username}", user)
                .replace("${password}", loginPassword)
                .replace("${unique}", UNIQUE_SUFFIX);
        // Compose a full URL when loginUrl is path-only ("/api/v1/users/login").
        // RestAssured.post(path) needs RestAssured.baseURI set, which the
        // writer-emitted setup in generated tests does — but the SUT
        // preflight runs BEFORE any generated test, so baseURI is unset
        // and the post hits localhost. base.url is pushed to System
        // properties by MistMain (see commit fixing this bug).
        String fullLoginUrl = loginUrl;
        if (loginUrl != null && !loginUrl.startsWith("http://")
                             && !loginUrl.startsWith("https://")) {
            String base = System.getProperty("base.url", "");
            if (!base.isEmpty()) {
                String sep = (base.endsWith("/") || loginUrl.startsWith("/")) ? "" : "/";
                String trimmedBase = base.endsWith("/") && loginUrl.startsWith("/")
                        ? base.substring(0, base.length() - 1) : base;
                fullLoginUrl = trimmedBase + sep + loginUrl;
            }
        }
        log.info("MstAuthHandler: POST {} (mode={}, user={})", fullLoginUrl, mode, user);
        return RestAssured.given()
                .contentType("application/json")
                .body(body)
                .when()
                .post(fullLoginUrl)
                .then()
                .statusCode(loginExpectedStatus)
                .extract().response();
    }
}
