package io.mist.core.auth;

import io.mist.core.coverage.StatusCodeTarget;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Strategies to manipulate authentication for testing auth-related status codes (401, 403).
 * 
 * This class provides various ways to modify authentication to trigger specific error responses:
 * - For 401 Unauthorized: Remove auth, invalid token, expired token, wrong credentials
 * - For 403 Forbidden: Use restricted user, guest account, insufficient scope
 */
public class AuthManipulationStrategy {
    
    private static final Logger log = LogManager.getLogger(AuthManipulationStrategy.class);
    
    /**
     * Types of authentication manipulation.
     */
    public enum ManipulationType {
        /** Remove authentication header entirely */
        REMOVE_AUTH("Remove all authentication headers"),
        
        /** Use a malformed/invalid token */
        INVALID_TOKEN("Use an invalid/malformed token"),
        
        /** Use an expired JWT token */
        EXPIRED_TOKEN("Use an expired token"),
        
        /** Use wrong username/password */
        WRONG_CREDENTIALS("Use incorrect credentials"),
        
        /** Valid token but missing required scope */
        INSUFFICIENT_SCOPE("Use token with insufficient scope"),
        
        /** Use a different user who shouldn't have access */
        WRONG_USER("Use a different user account"),
        
        /** Use anonymous/guest access */
        GUEST_USER("Use guest/anonymous access"),
        
        /** Keep original authentication */
        NO_MANIPULATION("Keep original authentication");
        
        private final String description;
        
        ManipulationType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Configuration for authentication after manipulation.
     */
    public static class AuthConfig {
        private final ManipulationType manipulationType;
        private final Map<String, String> headers;
        private String username;
        private String password;
        private String token;
        private boolean authEnabled;
        
        private AuthConfig(ManipulationType type) {
            this.manipulationType = type;
            this.headers = new HashMap<>();
            this.authEnabled = true;
        }
        
        public ManipulationType getManipulationType() { return manipulationType; }
        public Map<String, String> getHeaders() { return new HashMap<>(headers); }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getToken() { return token; }
        public boolean isAuthEnabled() { return authEnabled; }
        
        public void setHeader(String name, String value) { headers.put(name, value); }
        public void setUsername(String username) { this.username = username; }
        public void setPassword(String password) { this.password = password; }
        public void setToken(String token) { this.token = token; }
        public void setAuthEnabled(boolean enabled) { this.authEnabled = enabled; }
        
        @Override
        public String toString() {
            return String.format("AuthConfig{type=%s, enabled=%s, token=%s, user=%s}",
                manipulationType, authEnabled, 
                token != null ? token.substring(0, Math.min(20, token.length())) + "..." : "null",
                username);
        }
    }
    
    // Configuration values
    private String invalidToken = "INVALID_TOKEN_12345";
    private String expiredToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkV4cGlyZWQgVXNlciIsImlhdCI6MTUxNjIzOTAyMiwiZXhwIjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
    private String guestUsername = "guest";
    private String guestPassword = "guest";
    private String restrictedUsername = "restricted_user";
    private String restrictedPassword = "restricted_pass";
    
    /**
     * Default constructor with default values.
     */
    public AuthManipulationStrategy() {
    }
    
    /**
     * Create from properties.
     */
    public static AuthManipulationStrategy fromProperties(Properties props) {
        AuthManipulationStrategy strategy = new AuthManipulationStrategy();
        
        if (props.containsKey("status.code.auth.invalid.token")) {
            strategy.invalidToken = props.getProperty("status.code.auth.invalid.token");
        }
        if (props.containsKey("status.code.auth.expired.token")) {
            strategy.expiredToken = props.getProperty("status.code.auth.expired.token");
        }
        if (props.containsKey("status.code.auth.guest.user")) {
            strategy.guestUsername = props.getProperty("status.code.auth.guest.user");
        }
        if (props.containsKey("status.code.auth.guest.password")) {
            strategy.guestPassword = props.getProperty("status.code.auth.guest.password");
        }
        if (props.containsKey("status.code.auth.restricted.user")) {
            strategy.restrictedUsername = props.getProperty("status.code.auth.restricted.user");
        }
        if (props.containsKey("status.code.auth.restricted.password")) {
            strategy.restrictedPassword = props.getProperty("status.code.auth.restricted.password");
        }
        
        return strategy;
    }
    
    /**
     * Create from map properties.
     */
    public static AuthManipulationStrategy fromProperties(Map<String, String> props) {
        Properties properties = new Properties();
        properties.putAll(props);
        return fromProperties(properties);
    }
    
    /**
     * Get manipulated auth config based on the LLM-discovered target.
     * Analyzes the trigger strategy to determine the appropriate manipulation.
     */
    public AuthConfig getManipulatedAuth(StatusCodeTarget target, AuthConfig originalAuth) {
        if (!target.isRequiresAuthManipulation()) {
            log.debug("Target {} does not require auth manipulation", target.getStatusCode());
            return originalAuth != null ? originalAuth : createNoManipulation();
        }
        
        int targetCode = target.getStatusCode();
        String strategy = target.getTriggerStrategy().toLowerCase();
        
        log.info("Determining auth manipulation for status {} with strategy: {}", targetCode, strategy);
        
        // Determine manipulation type based on status code and strategy
        ManipulationType manipType = determineManipulationType(targetCode, strategy);
        
        return applyManipulation(manipType, originalAuth);
    }
    
    /**
     * Get auth config for a specific manipulation type.
     */
    public AuthConfig getManipulatedAuth(ManipulationType manipType, AuthConfig originalAuth) {
        return applyManipulation(manipType, originalAuth);
    }
    
    /**
     * Determine the manipulation type based on target status code and strategy text.
     */
    private ManipulationType determineManipulationType(int statusCode, String strategy) {
        // For 401 Unauthorized
        if (statusCode == 401) {
            if (strategy.contains("remove") || strategy.contains("no auth") || strategy.contains("missing")) {
                return ManipulationType.REMOVE_AUTH;
            }
            if (strategy.contains("invalid") || strategy.contains("malformed") || strategy.contains("bad token")) {
                return ManipulationType.INVALID_TOKEN;
            }
            if (strategy.contains("expired")) {
                return ManipulationType.EXPIRED_TOKEN;
            }
            if (strategy.contains("wrong") && (strategy.contains("credential") || strategy.contains("password"))) {
                return ManipulationType.WRONG_CREDENTIALS;
            }
            // Default for 401: remove auth
            return ManipulationType.REMOVE_AUTH;
        }
        
        // For 403 Forbidden
        if (statusCode == 403) {
            if (strategy.contains("guest") || strategy.contains("anonymous")) {
                return ManipulationType.GUEST_USER;
            }
            if (strategy.contains("wrong user") || strategy.contains("different user") || 
                strategy.contains("restricted") || strategy.contains("insufficient")) {
                return ManipulationType.WRONG_USER;
            }
            if (strategy.contains("scope") || strategy.contains("permission")) {
                return ManipulationType.INSUFFICIENT_SCOPE;
            }
            // Default for 403: use restricted user
            return ManipulationType.WRONG_USER;
        }
        
        // For other status codes that might need auth manipulation
        return ManipulationType.NO_MANIPULATION;
    }
    
    /**
     * Apply the manipulation to create a new AuthConfig.
     */
    private AuthConfig applyManipulation(ManipulationType manipType, AuthConfig original) {
        AuthConfig config = new AuthConfig(manipType);
        
        switch (manipType) {
            case REMOVE_AUTH:
                config.setAuthEnabled(false);
                log.info("Auth manipulation: REMOVE_AUTH - disabling authentication");
                break;
                
            case INVALID_TOKEN:
                config.setAuthEnabled(true);
                config.setToken(invalidToken);
                config.setHeader("Authorization", "Bearer " + invalidToken);
                log.info("Auth manipulation: INVALID_TOKEN - using invalid token");
                break;
                
            case EXPIRED_TOKEN:
                config.setAuthEnabled(true);
                config.setToken(expiredToken);
                config.setHeader("Authorization", "Bearer " + expiredToken);
                log.info("Auth manipulation: EXPIRED_TOKEN - using expired JWT");
                break;
                
            case WRONG_CREDENTIALS:
                config.setAuthEnabled(true);
                config.setUsername("invalid_user_" + System.currentTimeMillis());
                config.setPassword("wrong_password_123");
                log.info("Auth manipulation: WRONG_CREDENTIALS - using invalid credentials");
                break;
                
            case GUEST_USER:
                config.setAuthEnabled(true);
                config.setUsername(guestUsername);
                config.setPassword(guestPassword);
                log.info("Auth manipulation: GUEST_USER - using guest credentials");
                break;
                
            case WRONG_USER:
                config.setAuthEnabled(true);
                config.setUsername(restrictedUsername);
                config.setPassword(restrictedPassword);
                log.info("Auth manipulation: WRONG_USER - using restricted user credentials");
                break;
                
            case INSUFFICIENT_SCOPE:
                // For scope-based auth, we typically modify the token
                // This is a simplified version - in practice, you'd generate a token with limited scope
                config.setAuthEnabled(true);
                if (original != null && original.getToken() != null) {
                    config.setToken(original.getToken());
                    // In a real implementation, you'd modify the token claims
                }
                config.setHeader("X-Limited-Scope", "true");
                log.info("Auth manipulation: INSUFFICIENT_SCOPE - marking limited scope");
                break;
                
            case NO_MANIPULATION:
            default:
                // Copy original auth if available
                if (original != null) {
                    config.setAuthEnabled(original.isAuthEnabled());
                    config.setToken(original.getToken());
                    config.setUsername(original.getUsername());
                    config.setPassword(original.getPassword());
                    for (Map.Entry<String, String> header : original.getHeaders().entrySet()) {
                        config.setHeader(header.getKey(), header.getValue());
                    }
                }
                log.debug("Auth manipulation: NO_MANIPULATION - keeping original auth");
                break;
        }
        
        return config;
    }
    
    /**
     * Create a config with no manipulation (keep original auth).
     */
    public AuthConfig createNoManipulation() {
        return new AuthConfig(ManipulationType.NO_MANIPULATION);
    }
    
    /**
     * Create a config to remove authentication.
     */
    public AuthConfig createRemoveAuth() {
        return applyManipulation(ManipulationType.REMOVE_AUTH, null);
    }
    
    /**
     * Create a config with invalid token.
     */
    public AuthConfig createInvalidToken() {
        return applyManipulation(ManipulationType.INVALID_TOKEN, null);
    }
    
    /**
     * Create a config with expired token.
     */
    public AuthConfig createExpiredToken() {
        return applyManipulation(ManipulationType.EXPIRED_TOKEN, null);
    }
    
    /**
     * Create a config with guest user.
     */
    public AuthConfig createGuestUser() {
        return applyManipulation(ManipulationType.GUEST_USER, null);
    }
    
    /**
     * Create a config with restricted/wrong user.
     */
    public AuthConfig createWrongUser() {
        return applyManipulation(ManipulationType.WRONG_USER, null);
    }
    
    // Setters for configuration
    public void setInvalidToken(String token) { this.invalidToken = token; }
    public void setExpiredToken(String token) { this.expiredToken = token; }
    public void setGuestUsername(String username) { this.guestUsername = username; }
    public void setGuestPassword(String password) { this.guestPassword = password; }
    public void setRestrictedUsername(String username) { this.restrictedUsername = username; }
    public void setRestrictedPassword(String password) { this.restrictedPassword = password; }
}
