package io.mist.core.smart;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Authentication manager for Smart Input Fetching
 * Handles login and JWT token management for TrainTicket system
 */
public class SmartFetchAuthManager {

    private static final Logger log = LogManager.getLogger(SmartFetchAuthManager.class);

    private final String baseUrl;
    private final String adminUsername;
    private final String adminPassword;

    // Bug audit Finding #11: configurable login plumbing — was previously hardcoded to
    // TrainTicket's {@code /api/v1/users/login}, JSON keys {@code username}/{@code password},
    // response path {@code data.token}, and 30-minute expiry.
    private final String loginPath;
    private final String loginUsernameField;
    private final String loginPasswordField;
    private final String tokenJsonPath;
    private final int tokenValidityMinutes;

    // JWT token management. Reviewer Comment 4: synchronized accessor below to prevent
    // concurrent 401-retry races where multiple fetchers double-login simultaneously.
    private volatile String jwtToken;
    private final String jwtType = "Bearer";
    private volatile LocalDateTime tokenExpiry;

    public SmartFetchAuthManager(String baseUrl, String adminUsername, String adminPassword) {
        this(baseUrl, adminUsername, adminPassword,
                "/api/v1/users/login", "username", "password", "data.token", 30);
    }

    public SmartFetchAuthManager(String baseUrl, String adminUsername, String adminPassword,
                                  String loginPath, String loginUsernameField,
                                  String loginPasswordField, String tokenJsonPath,
                                  int tokenValidityMinutes) {
        this.baseUrl = baseUrl != null && baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.loginPath = loginPath != null ? loginPath : "/api/v1/users/login";
        this.loginUsernameField = loginUsernameField != null ? loginUsernameField : "username";
        this.loginPasswordField = loginPasswordField != null ? loginPasswordField : "password";
        this.tokenJsonPath = tokenJsonPath != null ? tokenJsonPath : "data.token";
        this.tokenValidityMinutes = tokenValidityMinutes > 0 ? tokenValidityMinutes : 30;

        log.info("SmartFetchAuthManager initialized for baseUrl: {}, username: {}, loginPath: {}",
                baseUrl, adminUsername, this.loginPath);
    }
    
    /**
     * Get valid JWT token, performing login if necessary. Reviewer Comment 4:
     * synchronized so concurrent fetchers do not all try to relogin simultaneously after
     * a token expires — the second caller waits and reuses the freshly-acquired token.
     */
    public synchronized String getValidToken() {
        if (isTokenValid()) {
            log.debug("Using existing valid JWT token");
            return jwtToken;
        }
        log.info("🔐 JWT token expired or missing, performing admin login...");
        return performLogin();
    }
    
    /**
     * Get authorization header value for API calls
     */
    public String getAuthorizationHeader() {
        String token = getValidToken();
        if (token != null) {
            return jwtType + " " + token;
        }
        return null;
    }
    
    /**
     * Check if current token is valid and not expired
     */
    private boolean isTokenValid() {
        if (jwtToken == null || tokenExpiry == null) {
            return false;
        }
        // Check if token expires within next 5 minutes (buffer for safety)
        LocalDateTime now = LocalDateTime.now();
        // Use min(5, half of validity window) so a 1-minute test token still works.
        long bufferMinutes = Math.min(5, Math.max(1, tokenValidityMinutes / 2));
        LocalDateTime expiryWithBuffer = tokenExpiry.minus(bufferMinutes, ChronoUnit.MINUTES);
        return now.isBefore(expiryWithBuffer);
    }
    
    /**
     * Perform admin login and get JWT token
     */
    private String performLogin() {
        try {
            String loginUrl = baseUrl + loginPath;
            log.info("🔐 Attempting admin login to: {}", loginUrl);

            URL url = new URL(loginUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            JSONObject loginPayload = new JSONObject();
            loginPayload.put(loginUsernameField, adminUsername);
            loginPayload.put(loginPasswordField, adminPassword);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = loginPayload.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            String responseBody = readResponse(conn);

            if (responseCode == 200) {
                JSONObject response = new JSONObject(responseBody);
                String extracted = extractTokenAtPath(response, tokenJsonPath);
                if (extracted != null && !extracted.isEmpty()) {
                    jwtToken = extracted;
                    tokenExpiry = LocalDateTime.now().plus(tokenValidityMinutes, ChronoUnit.MINUTES);
                    log.info("✅ Admin login successful, JWT token obtained (expires: {})", tokenExpiry);
                    // Fresh-review Finding F13: dropped the "JWT token: <prefix>..." debug log
                    // — even a 20-char prefix can leak base64url-encoded payload claims and
                    // is unnecessary now that the success log records the expiry.
                    return jwtToken;
                } else {
                    log.error("❌ Login response missing token at path '{}' (body redacted, length={})",
                            tokenJsonPath, responseBody == null ? 0 : responseBody.length());
                }
            } else {
                // Fresh-review Finding F13: redact the response body — a server error message
                // may echo the password attempt or include sensitive recovery hints.
                log.error("❌ Admin login failed with HTTP {} (body redacted, length={})",
                        responseCode, responseBody == null ? 0 : responseBody.length());
            }
        } catch (Exception e) {
            log.error("❌ Admin login failed with exception: {}", e.getMessage(), e);
        }
        jwtToken = null;
        tokenExpiry = null;
        return null;
    }

    /**
     * Extract a token from a dotted JSON path (e.g. {@code data.token} or {@code access_token}).
     * Returns {@code null} if any segment is missing or non-string at the leaf.
     */
    private static String extractTokenAtPath(JSONObject root, String dotted) {
        if (root == null || dotted == null || dotted.isEmpty()) return null;
        String[] parts = dotted.split("\\.");
        Object cursor = root;
        for (int i = 0; i < parts.length; i++) {
            if (!(cursor instanceof JSONObject)) return null;
            JSONObject obj = (JSONObject) cursor;
            String segment = parts[i];
            if (!obj.has(segment)) return null;
            cursor = obj.opt(segment);
        }
        return cursor instanceof String ? (String) cursor : null;
    }
    
    /**
     * Add authentication headers to an HTTP connection
     */
    public void addAuthHeaders(HttpURLConnection conn) {
        String authHeader = getAuthorizationHeader();
        if (authHeader != null) {
            conn.setRequestProperty("Authorization", authHeader);
            log.debug("Added Authorization header to request");
        } else {
            log.warn("⚠️ No valid authentication token available for API request");
        }
    }
    
    /**
     * Check if authentication is properly configured
     */
    public boolean isConfigured() {
        return adminUsername != null && !adminUsername.trim().isEmpty() &&
               adminPassword != null && !adminPassword.trim().isEmpty() &&
               baseUrl != null && !baseUrl.trim().isEmpty();
    }
    
    /**
     * Force token refresh on next request
     */
    public synchronized void invalidateToken() {
        log.info("🔄 Invalidating JWT token, will re-login on next request");
        jwtToken = null;
        tokenExpiry = null;
    }
    
    /**
     * Read response from HTTP connection
     */
    private String readResponse(HttpURLConnection conn) throws IOException {
        BufferedReader reader;
        
        // Try to read from input stream first, fall back to error stream
        try {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        } catch (IOException e) {
            // If input stream fails, try error stream
            if (conn.getErrorStream() != null) {
                reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            } else {
                throw e;
            }
        }
        
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        
        return response.toString();
    }
    
    /**
     * Get token expiry for debugging
     */
    public LocalDateTime getTokenExpiry() {
        return tokenExpiry;
    }
    
    /**
     * Check if we have a valid token without triggering login
     */
    public boolean hasValidToken() {
        return isTokenValid();
    }
    
    @Override
    public String toString() {
        return String.format("SmartFetchAuthManager{baseUrl='%s', username='%s', hasToken=%s, tokenExpiry=%s}",
                baseUrl, adminUsername, jwtToken != null, tokenExpiry);
    }
}
