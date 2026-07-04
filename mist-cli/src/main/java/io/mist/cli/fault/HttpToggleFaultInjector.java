package io.mist.cli.fault;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Toggles ts-inside-payment-service's drawback fault at RUNTIME via an in-memory HTTP endpoint
 * (GET .../inside_payment/test/faultmode/{mode}) rather than a pod restart. Because inside-payment
 * never restarts, ts-cancel-service's pooled Apache-HttpClient connection and its Ribbon instance
 * cache to the single stable pod are untouched — which is what makes the toggle reliable per leg.
 *
 * <p>Both restart-based mechanisms tried before race that client-side caching (g3-headtohead-results.md):
 * the EnvoyFilter mesh abort's convergence probe (fresh connection) leads the reused write-path
 * connection; the JAVA_TOOL_OPTIONS flag rollout leaves a stale instance so the caller either
 * round-robins the wrong flag (old pod still up) or read-timeout-hangs on the dead IP (old pod gone).
 * A runtime in-memory toggle on the un-restarted pod has neither failure mode and needs no settle.
 *
 * <p>The mode is derived from the {@link FaultTarget} property {@code mist.fault.drawback.<mode>.enabled}
 * → {@code <mode>} ("fail" or "fabricatedack"); {@link #clear} sets "none". An optional bearer token is
 * sent in case the gateway guards the route.
 */
public final class HttpToggleFaultInjector implements FaultInjector {

    private final String faultModeUrlBase;
    private final String bearerToken; // nullable
    private final int timeoutMs = 15000;

    /**
     * @param gatewayBaseUrl e.g. {@code http://localhost:18888}
     * @param bearerToken    optional JWT for the toggle GET (null when the route is open)
     */
    public HttpToggleFaultInjector(String gatewayBaseUrl, String bearerToken) {
        String base = gatewayBaseUrl.endsWith("/")
                ? gatewayBaseUrl.substring(0, gatewayBaseUrl.length() - 1) : gatewayBaseUrl;
        this.faultModeUrlBase = base + "/api/v1/inside_pay_service/inside_payment/test/faultmode";
        this.bearerToken = bearerToken;
    }

    @Override
    public void inject(FaultTarget target) {
        set(modeOf(target), "INJECT " + target);
    }

    @Override
    public void clear(FaultTarget target) {
        set("none", "CLEAR " + target);
    }

    /**
     * {@code mist.fault.drawback.fail.enabled} → {@code fail}; {@code ...fabricatedack...} →
     * {@code fabricatedack}. The fault family (segment 2) MUST be {@code drawback}: this injector
     * drives ONLY inside-payment's drawback faultmode endpoint, so accepting another family's
     * property (e.g. the agreement triple's {@code mist.fault.createaccount.fabricatedack.enabled})
     * would silently toggle the WRONG fault (round-2 review A/B).
     */
    static String modeOf(FaultTarget target) {
        String[] parts = target.property.split("\\.");
        if (parts.length < 4 || !"drawback".equals(parts[2]) || parts[3].trim().isEmpty()) {
            throw new FaultInjectionException("cannot derive a DRAWBACK fault mode from property '"
                    + target.property + "' (expected mist.fault.drawback.<mode>.enabled; this"
                    + " injector toggles only the drawback faultmode endpoint)");
        }
        return parts[3];
    }

    private void set(String mode, String op) {
        String url = faultModeUrlBase + "/" + mode;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            if (bearerToken != null && !bearerToken.isEmpty()) {
                c.setRequestProperty("Authorization", "Bearer " + bearerToken);
            }
            int code = c.getResponseCode();
            if (code / 100 != 2) {
                throw new FaultInjectionException(op + ": faultmode toggle GET " + url
                        + " returned HTTP " + code + " (route guarded, or inside-payment down?)");
            }
        } catch (IOException e) {
            throw new FaultInjectionException(op + ": faultmode toggle GET " + url
                    + " failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }
}
