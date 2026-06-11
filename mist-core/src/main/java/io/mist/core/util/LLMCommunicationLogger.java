package io.mist.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive LLM Communication Logger
 * 
 * Logs all LLM communications (requests and responses) with detailed metadata
 * including response times, model information, and content for debugging and analysis.
 * 
 * Features:
 * - Supports all LLM models (Local, Gemini, Ollama)
 * - Detailed request/response logging with timestamps
 * - Response time measurements
 * - Configurable content truncation
 * - Thread-safe operation
 * - Automatic log file creation with timestamps
 * - System resource monitoring for local models (CPU/Memory usage)
 */
public class LLMCommunicationLogger {
    
    private static final Logger log = LoggerFactory.getLogger(LLMCommunicationLogger.class);
    
    private static LLMCommunicationLogger instance;
    private static final Object lock = new Object();
    
    // Configuration
    private final boolean enabled;
    private final String logDir;
    private final String filePrefix;
    private final boolean includeResponseTime;
    private final boolean includeContent;
    private final boolean includeMetadata;
    private final String logLevel;
    private final int maxContentLength;
    
    // State
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final String sessionId;
    private PrintWriter logWriter;
    private final Object writerLock = new Object();
    
    // Resource monitoring
    private final SystemResourceMonitor resourceMonitor;
    
    // Formatters
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    
    private LLMCommunicationLogger(Properties properties) {
        this.enabled = Boolean.parseBoolean(properties.getProperty("llm.communication.logging.enabled", "true"));
        this.logDir = properties.getProperty("llm.communication.logging.dir", "logs/llm-communications");
        this.filePrefix = properties.getProperty("llm.communication.logging.file.prefix", "llm-communication");
        this.includeResponseTime = Boolean.parseBoolean(properties.getProperty("llm.communication.logging.include.response.time", "true"));
        this.includeContent = Boolean.parseBoolean(properties.getProperty("llm.communication.logging.include.content", "true"));
        this.includeMetadata = Boolean.parseBoolean(properties.getProperty("llm.communication.logging.include.metadata", "true"));
        this.logLevel = properties.getProperty("llm.communication.logging.level", "INFO");
        this.maxContentLength = Integer.parseInt(properties.getProperty("llm.communication.logging.max.content.length", "10000"));
        
        this.sessionId = generateSessionId();
        
        // Initialize resource monitoring for local models
        try {
            this.resourceMonitor = SystemResourceMonitor.getInstance(properties);
            log.info("SystemResourceMonitor initialized successfully. Enabled: {}", 
                    resourceMonitor != null ? resourceMonitor.isEnabled() : "null");
        } catch (Exception e) {
            log.error("Failed to initialize SystemResourceMonitor: {}", e.getMessage(), e);
            throw e;
        }
        
        if (enabled) {
            initializeLogFile();
        }
    }
    
    /**
     * Get singleton instance
     */
    public static LLMCommunicationLogger getInstance(Properties properties) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new LLMCommunicationLogger(properties);
                }
            }
        }
        return instance;
    }
    
    /**
     * Get existing instance (must be initialized first)
     */
    public static LLMCommunicationLogger getInstance() {
        if (instance == null) {
            throw new IllegalStateException("LLMCommunicationLogger not initialized. Call getInstance(Properties) first.");
        }
        return instance;
    }
    
    /**
     * Log LLM request
     */
    public LLMRequestContext logRequest(String modelType, String modelName, String systemPrompt, String userPrompt, 
                                       String endpoint, Object additionalMetadata) {
        if (!enabled) {
            return new LLMRequestContext(0, System.currentTimeMillis(), false);
        }
        
        long requestId = requestCounter.incrementAndGet();
        long startTime = System.currentTimeMillis();
        
        // Start resource monitoring for local models
        SystemResourceMonitor.ResourceMonitoringContext resourceContext = null;
        log.debug("Checking resource monitoring: resourceMonitor={}, modelType={}", 
                 resourceMonitor != null ? "initialized" : "null", modelType);
        
        if (resourceMonitor != null && resourceMonitor.shouldMonitor(modelType)) {
            log.debug("Starting resource monitoring for {} model", modelType);
            resourceContext = resourceMonitor.startMonitoring(requestId, modelType, modelName);
            log.debug("Resource monitoring context created: {}", 
                     resourceContext != null && resourceContext.isMonitoring() ? "active" : "inactive");
        } else {
            log.debug("Resource monitoring not started. Monitor null: {}, Should monitor: {}", 
                     resourceMonitor == null, 
                     resourceMonitor != null ? resourceMonitor.shouldMonitor(modelType) : "N/A");
        }
        
        try {
            synchronized (writerLock) {
                if (logWriter != null) {
                    logWriter.println("================================================================================");
                    logWriter.println("🚀 LLM REQUEST #" + requestId);
                    logWriter.println("================================================================================");
                    logWriter.println("Timestamp: " + LocalDateTime.now().format(TIMESTAMP_FORMATTER));
                    logWriter.println("Session ID: " + sessionId);
                    logWriter.println("Request ID: " + requestId);
                    
                    if (includeMetadata) {
                        logWriter.println("Model Type: " + (modelType != null ? modelType : "Unknown"));
                        logWriter.println("Model Name: " + (modelName != null ? modelName : "Unknown"));
                        logWriter.println("Endpoint: " + (endpoint != null ? endpoint : "Unknown"));
                        if (additionalMetadata != null) {
                            logWriter.println("Additional Metadata: " + additionalMetadata.toString());
                        }
                        
                        // Log resource monitoring status for local models
                        if ("LOCAL".equalsIgnoreCase(modelType) || "OLLAMA".equalsIgnoreCase(modelType)) {
                            if (resourceMonitor != null && resourceMonitor.isEnabled()) {
                                if (resourceContext != null && resourceContext.isMonitoring()) {
                                    logWriter.println("Resource Monitoring: ✅ ENABLED & ACTIVE (Local Model) - Monitoring system resources");
                                } else {
                                    logWriter.println("Resource Monitoring: ⚠️ ENABLED but NOT ACTIVE (Check logs for errors)");
                                }
                            } else {
                                String reason = resourceMonitor == null ? "not initialized" : "disabled in config";
                                logWriter.println("Resource Monitoring: ❌ DISABLED (" + reason + ")");
                            }
                        } else {
                            logWriter.println("Resource Monitoring: ⏭️ SKIPPED (Online Model)");
                        }
                    }
                    
                    if (includeContent) {
                        logWriter.println("--------------------------------------------------------------------------------");
                        logWriter.println("📝 SYSTEM PROMPT:");
                        logWriter.println(truncateContent(systemPrompt));
                        logWriter.println("--------------------------------------------------------------------------------");
                        logWriter.println("👤 USER PROMPT:");
                        logWriter.println(truncateContent(userPrompt));
                        logWriter.println("--------------------------------------------------------------------------------");
                    }
                    
                    logWriter.flush();
                }
            }
        } catch (Exception e) {
            log.error("Failed to log LLM request: {}", e.getMessage());
        }
        
        return new LLMRequestContext(requestId, startTime, true, resourceContext);
    }
    
    /**
     * Log LLM response
     */
    public void logResponse(LLMRequestContext context, String response, boolean success, String errorMessage) {
        if (!enabled || !context.isLogged()) {
            return;
        }
        
        long endTime = System.currentTimeMillis();
        long responseTime = endTime - context.getStartTime();
        
        // Resource monitoring data is captured at response time
        
        try {
            synchronized (writerLock) {
                if (logWriter != null) {
                    logWriter.println("🎯 LLM RESPONSE #" + context.getRequestId());
                    logWriter.println("Timestamp: " + LocalDateTime.now().format(TIMESTAMP_FORMATTER));
                    logWriter.println("Status: " + (success ? "✅ SUCCESS" : "❌ FAILED"));
                    
                    if (includeResponseTime) {
                        logWriter.println("Response Time: " + responseTime + " ms");
                    }
                    
                    // Log resource monitoring summary if available
                    if (context.getResourceContext() != null && context.getResourceContext().isMonitoring()) {
                        String monitoringSummary = resourceMonitor.stopMonitoring(context.getResourceContext());
                        if (!monitoringSummary.equals("No monitoring data available")) {
                            logWriter.println("📊 Resource Usage During Generation: " + monitoringSummary);
                        }
                    }
                    
                    if (!success && errorMessage != null) {
                        logWriter.println("Error Message: " + errorMessage);
                    }
                    
                    if (includeContent && response != null) {
                        logWriter.println("--------------------------------------------------------------------------------");
                        logWriter.println("🤖 LLM RESPONSE:");
                        logWriter.println(truncateContent(response));
                        logWriter.println("--------------------------------------------------------------------------------");
                    }
                    
                    logWriter.println("================================================================================");
                    logWriter.println(); // Empty line for readability
                    logWriter.flush();
                }
            }
        } catch (Exception e) {
            log.error("Failed to log LLM response: {}", e.getMessage());
        }
    }
    
    /**
     * Log simple LLM interaction (request + response in one call)
     */
    public void logInteraction(String modelType, String modelName, String systemPrompt, String userPrompt,
                              String response, long responseTimeMs, boolean success, String errorMessage,
                              String endpoint, Object additionalMetadata) {
        LLMRequestContext context = logRequest(modelType, modelName, systemPrompt, userPrompt, endpoint, additionalMetadata);
        
        // Adjust the start time to account for the provided response time
        context.setStartTime(System.currentTimeMillis() - responseTimeMs);
        
        logResponse(context, response, success, errorMessage);
    }
    
    /**
     * Initialize log file
     */
    private void initializeLogFile() {
        try {
            // Create log directory if it doesn't exist
            File logDirFile = new File(logDir);
            if (!logDirFile.exists()) {
                boolean created = logDirFile.mkdirs();
                if (!created) {
                    log.error("Failed to create LLM communication log directory: {}", logDir);
                    return;
                }
            }
            
            // Create log file with timestamp
            String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FORMATTER);
            String fileName = filePrefix + "-" + timestamp + ".log";
            File logFile = new File(logDirFile, fileName);
            
            logWriter = new PrintWriter(new FileWriter(logFile, true));
            
            // Write header
            logWriter.println("################################################################################");
            logWriter.println("# LLM COMMUNICATION LOG");
            logWriter.println("# Session ID: " + sessionId);
            logWriter.println("# Started: " + LocalDateTime.now().format(TIMESTAMP_FORMATTER));
            logWriter.println("# Configuration:");
            logWriter.println("#   - Include Response Time: " + includeResponseTime);
            logWriter.println("#   - Include Content: " + includeContent);
            logWriter.println("#   - Include Metadata: " + includeMetadata);
            logWriter.println("#   - Max Content Length: " + (maxContentLength == -1 ? "Unlimited" : maxContentLength));
            logWriter.println("#   - Log Level: " + logLevel);
            logWriter.println("################################################################################");
            logWriter.println();
            logWriter.flush();
            
            log.info("LLM communication logging initialized: {}", logFile.getAbsolutePath());
            
        } catch (IOException e) {
            log.error("Failed to initialize LLM communication log file: {}", e.getMessage());
        }
    }
    
    /**
     * Generate unique session ID
     */
    private String generateSessionId() {
        return "LLM-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
    
    /**
     * Truncate content if it exceeds maximum length
     */
    private String truncateContent(String content) {
        if (content == null) {
            return "[NULL]";
        }
        
        if (maxContentLength == -1 || content.length() <= maxContentLength) {
            return content;
        }
        
        return content.substring(0, maxContentLength) + "\n... [TRUNCATED - Original length: " + content.length() + " characters]";
    }
    
    /**
     * Close logger and cleanup resources
     */
    public void close() {
        if (enabled && logWriter != null) {
            synchronized (writerLock) {
                logWriter.println("################################################################################");
                logWriter.println("# LLM COMMUNICATION LOG ENDED");
                logWriter.println("# Session ID: " + sessionId);
                logWriter.println("# Ended: " + LocalDateTime.now().format(TIMESTAMP_FORMATTER));
                logWriter.println("# Total Requests: " + requestCounter.get());
                logWriter.println("################################################################################");
                logWriter.close();
                logWriter = null;
            }
        }
    }
    
    /**
     * Check if logging is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Context object for tracking LLM requests
     */
    public static class LLMRequestContext {
        private final long requestId;
        private long startTime;
        private final boolean logged;
        private final SystemResourceMonitor.ResourceMonitoringContext resourceContext;
        
        public LLMRequestContext(long requestId, long startTime, boolean logged) {
            this(requestId, startTime, logged, null);
        }
        
        public LLMRequestContext(long requestId, long startTime, boolean logged, 
                               SystemResourceMonitor.ResourceMonitoringContext resourceContext) {
            this.requestId = requestId;
            this.startTime = startTime;
            this.logged = logged;
            this.resourceContext = resourceContext;
        }
        
        public long getRequestId() { return requestId; }
        public long getStartTime() { return startTime; }
        public boolean isLogged() { return logged; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public SystemResourceMonitor.ResourceMonitoringContext getResourceContext() { return resourceContext; }
    }
}
