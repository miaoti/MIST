package io.mist.cli.auth;

import io.restassured.filter.OrderedFilter;
import io.restassured.filter.FilterContext;
import io.restassured.http.Method;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * RestAssured filter used <strong>only</strong> by the MST writer. When the
 * server answers a request with HTTP <strong>401 or 403</strong>, this filter:
 *
 * <ol>
 *   <li>invalidates the cached JWT in {@link MstAuthHandler},</li>
 *   <li>triggers a fresh login via {@link MstAuthHandler#ensureReady()},</li>
 *   <li>swaps the {@code Authorization} header for the new token, and</li>
 *   <li>retries the request <em>once</em>.</li>
 * </ol>
 *
 * If the second call also returns 401/403, that response is returned to the
 * test - no infinite loop.
 *
 * <p><strong>Why 403 in addition to 401?</strong> RFC 7235 says servers should
 * return 401 when authentication is missing/expired and 403 only after the
 * caller has been authenticated but lacks privilege. In practice many Spring
 * Security setups (train-ticket included) return 403 in both cases — a default
 * JWT filter that throws an unhandled exception ends up routed through the
 * AccessDeniedHandler, not the AuthenticationEntryPoint. Treating 403 as a
 * possible auth-staleness signal lets tests survive multi-hour runs where the
 * cached JWT expires partway through; the cost of a legitimate 403 (admin
 * authenticated but lacks role for a specific endpoint) is one wasted re-login
 * before the original 403 is returned.
 *
 * <p>The MST writer only attaches this filter on steps whose auth flow follows
 * the configured default. Steps with a per-test override (the
 * {@code __mstOverrideToken} / {@code __mstDisableAuth} locals set by the
 * {@code AuthManipulationStrategy}) bypass the filter so that exploration
 * tests targeting 401/403 (INVALID_TOKEN, EXPIRED_TOKEN, REMOVE_AUTH) actually
 * observe the response code they were designed to trigger.
 *
 * <p>Classic RESTest writers ({@code RESTAssuredWriter}) never reference this
 * class - it only ships in the MST code path.
 */
public final class MstAuthRefreshFilter implements OrderedFilter {

    private static final Logger log = LogManager.getLogger(MstAuthRefreshFilter.class);

    public static final MstAuthRefreshFilter INSTANCE = new MstAuthRefreshFilter();

    // Reentry guard. The retry below re-issues via requestSpec.request(...) which
    // re-runs the full filter chain (and therefore this filter), so without a
    // guard a still-bad token would loop forever. ThreadLocal because RestAssured
    // executes filters on the calling thread.
    private static final ThreadLocal<Boolean> RETRYING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private MstAuthRefreshFilter() {}

    @Override
    public Response filter(FilterableRequestSpecification requestSpec,
                           FilterableResponseSpecification responseSpec,
                           FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);

        if (response == null) return response;
        if (RETRYING.get()) return response;
        int status = response.getStatusCode();
        // Refresh on either 401 (Unauthorized — well-behaved services) or 403
        // (Forbidden — services that route expired/invalid-JWT failures through
        // the AccessDeniedHandler instead of the AuthenticationEntryPoint).
        if (status != 401 && status != 403) return response;
        if (!MstAuthHandler.isRefreshOn401Enabled()) return response;

        String header = MstAuthHandler.getTokenHeader();
        // Don't refresh if the request had no Authorization header at all -
        // means the caller deliberately opted out (skip pattern, NONE mode,
        // or per-test override gone wrong); refreshing would change semantics.
        if (requestSpec.getHeaders().getValue(header) == null) return response;

        // Skip the refresh+retry when the cached token was minted less than
        // 5s ago. Under parallel execution N concurrent 403s would otherwise
        // each invalidate the cache + re-login + retry, multiplying load on
        // the login endpoint and producing N identical 403s. A fresh token
        // can't be "expired"; the 403 is a real authorization failure
        // (e.g. role-gated endpoint), so return it as-is.
        long ageNs = MstAuthHandler.nanosSinceTokenSet();
        // Threshold reads from MstConfig.adaptive().authTokenMinAgeNs(), defaulting
        // to 5s (unchanged behaviour). Lets operators tune per-deployment without
        // recompiling.
        long minAgeNs = io.mist.core.config.MstConfig.instance().adaptive().authTokenMinAgeNs();
        if (ageNs < minAgeNs) {
            return response;
        }

        log.info("MstAuthRefreshFilter: {} received from {} {}, refreshing token and retrying once",
                status, requestSpec.getMethod(), requestSpec.getURI());

        MstAuthHandler.invalidate();
        if (!MstAuthHandler.ensureReady()) {
            log.warn("MstAuthRefreshFilter: re-login failed, returning original {}", status);
            return response;
        }

        String newToken = MstAuthHandler.getDefaultToken();
        if (newToken == null || newToken.isEmpty()) return response;

        // Swap the Authorization header for the new value.
        requestSpec.removeHeader(header);
        requestSpec.header(header, MstAuthHandler.getTokenPrefix() + newToken);

        // Why: RestAssured 4.2.0's FilterContextImpl backs ctx.next() with a
        // single-pass iterator over the filter chain. The first ctx.next()
        // above already drained it (the terminal SendRequestFilter ran), so a
        // second ctx.next() falls through to `return null` and the caller's
        // ".then()" NPEs. Re-issue through the request spec instead — that
        // builds a fresh filter chain. The RETRYING flag short-circuits this
        // filter on the recursive entry so we don't loop on a still-bad token.
        Response retried;
        RETRYING.set(Boolean.TRUE);
        try {
            Method httpMethod = Method.valueOf(requestSpec.getMethod().toUpperCase());
            // Strip any pre-serialized query string before re-issuing: RestAssured
            // 4.2.0 still has the spec's queryParam state populated, so passing a
            // URL that already contains "?a=1" would result in "?a=1&a=1" after
            // the spec re-applies its query params on top. If getURI() returns no
            // query string, the substring is a no-op.
            String uri = requestSpec.getURI();
            int q = uri.indexOf('?');
            String uriWithoutQuery = (q >= 0) ? uri.substring(0, q) : uri;
            retried = requestSpec.request(httpMethod, uriWithoutQuery);
        } finally {
            // remove() instead of set(FALSE) so the ThreadLocal map entry does not
            // outlive this thread (matters for thread pools where the same Thread
            // is reused across many tests).
            RETRYING.remove();
        }
        if (retried != null) {
            int retriedStatus = retried.getStatusCode();
            if (retriedStatus == 401 || retriedStatus == 403) {
                log.warn("MstAuthRefreshFilter: retry also returned {} - token may be revoked, " +
                        "auth.login.* misconfigured, or the caller genuinely lacks privilege",
                        retriedStatus);
            }
        }
        // Preserve the original 401/403 if the retry produced null — gives the
        // caller a meaningful status to react to instead of the NPE-via-null
        // chain we just fixed.
        return retried != null ? retried : response;
    }

    /** Run after the auth header is already on the request. */
    @Override
    public int getOrder() {
        return Integer.MAX_VALUE;
    }
}
