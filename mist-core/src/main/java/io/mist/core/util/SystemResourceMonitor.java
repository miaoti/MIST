package io.mist.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * System Resource Monitor for Local LLM Model Processes
 * 
 * Monitors CPU and memory usage of external LLM processes (GPT4All, Ollama desktop apps)
 * NOT the Java application itself, but the actual model inference processes.
 * 
 * Features:
 * - Detects and monitors GPT4All.exe and ollama.exe processes
 * - Tracks system-wide resource usage during LLM generation
 * - Real-time process monitoring during model inference
 * - Statistical analysis (average, peak usage)
 */
public class SystemResourceMonitor {
    
    private static final Logger log = LoggerFactory.getLogger(SystemResourceMonitor.class);
    
    private static SystemResourceMonitor instance;
    private static final Object lock = new Object();
    
    // Configuration
    private final boolean enabled;
    private final long monitoringIntervalMs;
    private final boolean includeCpuUsage;
    private final boolean includeDetailedMemory;
    
    // Monitoring components
    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    private final ScheduledExecutorService executor;
    
    // State
    private final AtomicLong monitoringCounter = new AtomicLong(0);
    private final String sessionId;
    
    // Formatters
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    // Process patterns for different model types
    private static final Pattern OLLAMA_PROCESS = Pattern.compile("ollama.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern GPT4ALL_PROCESS = Pattern.compile("gpt4all.*|chat.*", Pattern.CASE_INSENSITIVE);
    
    private SystemResourceMonitor(Properties properties) {
        this.enabled = Boolean.parseBoolean(properties.getProperty("llm.resource.monitoring.enabled", "true"));
        this.monitoringIntervalMs = Long.parseLong(properties.getProperty("llm.resource.monitoring.interval.ms", "500"));
        this.includeCpuUsage = Boolean.parseBoolean(properties.getProperty("llm.resource.monitoring.include.cpu", "true"));
        this.includeDetailedMemory = Boolean.parseBoolean(properties.getProperty("llm.resource.monitoring.include.detailed.memory", "true"));
        
        this.sessionId = generateSessionId();
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.executor = Executors.newScheduledThreadPool(1);
        
        log.info("SystemResourceMonitor initialized for external LLM processes. Enabled: {}, Interval: {}ms, CPU: {}, Memory: {}", 
                enabled, monitoringIntervalMs, includeCpuUsage, includeDetailedMemory);
        
        // Log available properties for debugging
        log.debug("Resource monitoring properties received: {}", properties.stringPropertyNames());
    }
    
    /**
     * Get singleton instance
     */
    public static SystemResourceMonitor getInstance(Properties properties) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new SystemResourceMonitor(properties);
                }
            }
        }
        return instance;
    }
    
    /**
     * Get existing instance (must be initialized first)
     */
    public static SystemResourceMonitor getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SystemResourceMonitor not initialized. Call getInstance(Properties) first.");
        }
        return instance;
    }
    
    /**
     * Check if monitoring should be enabled for the given model type
     */
    public boolean shouldMonitor(String modelType) {
        boolean result = enabled && ("LOCAL".equalsIgnoreCase(modelType) || "OLLAMA".equalsIgnoreCase(modelType));
        log.debug("shouldMonitor({}) = {} (enabled: {}, modelType matches: {})", 
                 modelType, result, enabled, 
                 "LOCAL".equalsIgnoreCase(modelType) || "OLLAMA".equalsIgnoreCase(modelType));
        return result;
    }
    
    /**
     * Start monitoring external LLM processes during generation
     */
    public ResourceMonitoringContext startMonitoring(long requestId, String modelType, String modelName) {
        if (!shouldMonitor(modelType)) {
            return new ResourceMonitoringContext(requestId, false, null, null);
        }
        
        long monitoringId = monitoringCounter.incrementAndGet();
        
        // Create a monitoring task that will track external processes
        ExternalProcessMonitoringTask monitoringTask = new ExternalProcessMonitoringTask(
            monitoringId, requestId, modelType, modelName);
        
        // Start periodic monitoring
        ScheduledFuture<?> scheduledTask = executor.scheduleAtFixedRate(
            monitoringTask,
            0, // Start immediately
            monitoringIntervalMs,
            TimeUnit.MILLISECONDS
        );
        
        log.debug("Started external process monitoring for {} model (request {})", modelType, requestId);
        
        return new ResourceMonitoringContext(requestId, true, scheduledTask, monitoringTask);
    }
    
    /**
     * Stop monitoring and get the resource usage summary
     */
    public String stopMonitoring(ResourceMonitoringContext context) {
        if (!context.isMonitoring() || context.getMonitoringTask() == null) {
            return "No monitoring data available";
        }
        
        try {
            // Stop the monitoring task
            if (context.getScheduledTask() != null) {
                context.getScheduledTask().cancel(false);
            }
            
            // Get the monitoring summary
            ExternalProcessMonitoringTask task = context.getMonitoringTask();
            return task.getMonitoringSummary();
            
        } catch (Exception e) {
            log.error("Failed to stop external process monitoring: {}", e.getMessage());
            return "Error retrieving monitoring data";
        }
    }
    
    /**
     * Task that monitors external LLM processes during generation
     */
    private class ExternalProcessMonitoringTask implements Runnable {
        private final long monitoringId;
        private final long requestId;
        private final String modelType;
        private final String modelName;
        private final List<SystemResourceSnapshot> snapshots;
        private final long startTime;
        
        public ExternalProcessMonitoringTask(long monitoringId, long requestId, String modelType, String modelName) {
            this.monitoringId = monitoringId;
            this.requestId = requestId;
            this.modelType = modelType;
            this.modelName = modelName;
            this.snapshots = new ArrayList<>();
            this.startTime = System.currentTimeMillis();
        }
        
        @Override
        public void run() {
            try {
                SystemResourceSnapshot snapshot = captureSystemResourceSnapshot();
                synchronized (snapshots) {
                    snapshots.add(snapshot);
                }
                
                if (snapshots.size() % 5 == 0) { // Log every 5 samples for debugging
                    log.debug("Captured {} system resource samples for {} model", snapshots.size(), modelType);
                }
                
            } catch (Exception e) {
                log.debug("Error capturing system resource snapshot: {}", e.getMessage());
            }
        }
        
        /**
         * Capture system-wide resource snapshot, focusing on LLM processes
         */
        private SystemResourceSnapshot captureSystemResourceSnapshot() {
            long timestamp = System.currentTimeMillis();
            
            // Get system-wide CPU usage
            double systemCpuUsage = getSystemCpuUsage();
            
            // Get total system memory usage
            SystemMemoryInfo memoryInfo = getSystemMemoryInfo();
            
            // Try to get specific process information for the LLM model
            ProcessInfo llmProcessInfo = getLLMProcessInfo(modelType);
            
            return new SystemResourceSnapshot(timestamp, systemCpuUsage, memoryInfo, llmProcessInfo);
        }
        
        /**
         * Get system-wide CPU usage
         */
        private double getSystemCpuUsage() {
            try {
                if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                    com.sun.management.OperatingSystemMXBean sunBean = 
                        (com.sun.management.OperatingSystemMXBean) osBean;
                    // Get SYSTEM CPU load, not process CPU load
                    return sunBean.getSystemCpuLoad() * 100.0;
                } else {
                    // Fallback: system load average
                    double loadAverage = osBean.getSystemLoadAverage();
                    int availableProcessors = osBean.getAvailableProcessors();
                    if (loadAverage >= 0) {
                        return (loadAverage / availableProcessors) * 100.0;
                    }
                }
            } catch (Exception e) {
                log.debug("Could not get system CPU usage: {}", e.getMessage());
            }
            return -1.0;
        }
        
        /**
         * Get system memory information
         */
        private SystemMemoryInfo getSystemMemoryInfo() {
            try {
                if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                    com.sun.management.OperatingSystemMXBean sunBean = 
                        (com.sun.management.OperatingSystemMXBean) osBean;
                    
                    long totalPhysicalMemory = sunBean.getTotalPhysicalMemorySize();
                    long freePhysicalMemory = sunBean.getFreePhysicalMemorySize();
                    long usedPhysicalMemory = totalPhysicalMemory - freePhysicalMemory;
                    double memoryPercent = (double) usedPhysicalMemory / totalPhysicalMemory * 100.0;
                    
                    return new SystemMemoryInfo(totalPhysicalMemory, usedPhysicalMemory, memoryPercent);
                }
            } catch (Exception e) {
                log.debug("Could not get system memory info: {}", e.getMessage());
            }
            
            // Fallback to JVM memory (less accurate for system monitoring)
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            double memoryPercent = (double) usedMemory / maxMemory * 100.0;
            
            return new SystemMemoryInfo(maxMemory, usedMemory, memoryPercent);
        }
        
        /**
         * Get process information for LLM model (Windows-specific implementation)
         */
        private ProcessInfo getLLMProcessInfo(String modelType) {
            try {
                String processName = getProcessName(modelType);
                if (processName == null) {
                    return null;
                }
                
                // Use Windows tasklist command to get process information
                ProcessBuilder pb = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq " + processName, "/FO", "CSV");
                Process process = pb.start();
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains(processName)) {
                            // Parse CSV output: "Image Name","PID","Session Name","Session#","Mem Usage"
                            String[] parts = line.split("\",\"");
                            if (parts.length >= 5) {
                                String memUsageStr = parts[4].replace("\"", "").replace(",", "").replace(" K", "");
                                try {
                                    long memoryKB = Long.parseLong(memUsageStr);
                                    return new ProcessInfo(processName, memoryKB * 1024); // Convert to bytes
                                } catch (NumberFormatException e) {
                                    log.debug("Could not parse memory usage for {}: {}", processName, memUsageStr);
                                }
                            }
                        }
                    }
                }
                
                process.waitFor(2, TimeUnit.SECONDS);
                
            } catch (Exception e) {
                log.debug("Could not get process info for {}: {}", modelType, e.getMessage());
            }
            
            return null;
        }
        
        /**
         * Get the process name for the given model type
         */
        private String getProcessName(String modelType) {
            switch (modelType.toUpperCase()) {
                case "OLLAMA":
                    return "ollama.exe";
                case "LOCAL":
                    return "gpt4all.exe"; // Assuming GPT4All desktop app
                default:
                    return null;
            }
        }
        
        /**
         * Generate monitoring summary from all captured snapshots
         */
        public String getMonitoringSummary() {
            synchronized (snapshots) {
                if (snapshots.isEmpty()) {
                    return "No system resource data captured";
                }
                
                // Calculate system-wide statistics
                double avgSystemCpu = snapshots.stream()
                    .filter(s -> s.systemCpuUsage >= 0)
                    .mapToDouble(s -> s.systemCpuUsage)
                    .average()
                    .orElse(-1.0);
                
                double maxSystemCpu = snapshots.stream()
                    .filter(s -> s.systemCpuUsage >= 0)
                    .mapToDouble(s -> s.systemCpuUsage)
                    .max()
                    .orElse(-1.0);
                
                double avgSystemMemory = snapshots.stream()
                    .mapToDouble(s -> s.memoryInfo.memoryPercent)
                    .average()
                    .orElse(0.0);
                
                double maxSystemMemory = snapshots.stream()
                    .mapToDouble(s -> s.memoryInfo.memoryPercent)
                    .max()
                    .orElse(0.0);
                
                long maxMemoryBytes = snapshots.stream()
                    .mapToLong(s -> s.memoryInfo.usedMemory)
                    .max()
                    .orElse(0L);
                
                long totalMemoryBytes = snapshots.isEmpty() ? 0L : snapshots.get(0).memoryInfo.totalMemory;
                
                StringBuilder summary = new StringBuilder();
                
                // System CPU statistics
                if (includeCpuUsage && avgSystemCpu >= 0) {
                    summary.append(String.format("System CPU: avg=%.1f%%, max=%.1f%%", avgSystemCpu, maxSystemCpu));
                } else if (includeCpuUsage) {
                    summary.append("System CPU: N/A");
                }
                
                // System Memory statistics
                if (summary.length() > 0) summary.append(" | ");
                summary.append(String.format("System Memory: avg=%.1f%%, max=%.1f%% (%s/%s)", 
                    avgSystemMemory, maxSystemMemory, 
                    formatBytes(maxMemoryBytes), 
                    formatBytes(totalMemoryBytes)));
                
                // Process-specific information if available
                long processMemoryCount = snapshots.stream()
                    .filter(s -> s.processInfo != null)
                    .count();
                
                if (processMemoryCount > 0) {
                    long maxProcessMemory = snapshots.stream()
                        .filter(s -> s.processInfo != null)
                        .mapToLong(s -> s.processInfo.memoryBytes)
                        .max()
                        .orElse(0L);
                    
                    String processName = getProcessName(modelType);
                    if (maxProcessMemory > 0) {
                        summary.append(String.format(" | %s Process: max=%s", 
                            processName != null ? processName : modelType, 
                            formatBytes(maxProcessMemory)));
                    }
                }
                
                // Add sampling information
                long duration = System.currentTimeMillis() - startTime;
                summary.append(String.format(" | Samples: %d over %dms", snapshots.size(), duration));
                
                return summary.toString();
            }
        }
    }
    
    /**
     * Data classes for resource information
     */
    private static class SystemResourceSnapshot {
        final long timestamp;
        final double systemCpuUsage;
        final SystemMemoryInfo memoryInfo;
        final ProcessInfo processInfo;
        
        SystemResourceSnapshot(long timestamp, double systemCpuUsage, 
                             SystemMemoryInfo memoryInfo, ProcessInfo processInfo) {
            this.timestamp = timestamp;
            this.systemCpuUsage = systemCpuUsage;
            this.memoryInfo = memoryInfo;
            this.processInfo = processInfo;
        }
    }
    
    private static class SystemMemoryInfo {
        final long totalMemory;
        final long usedMemory;
        final double memoryPercent;
        
        SystemMemoryInfo(long totalMemory, long usedMemory, double memoryPercent) {
            this.totalMemory = totalMemory;
            this.usedMemory = usedMemory;
            this.memoryPercent = memoryPercent;
        }
    }
    
    private static class ProcessInfo {
        final String processName;
        final long memoryBytes;
        
        ProcessInfo(String processName, long memoryBytes) {
            this.processName = processName;
            this.memoryBytes = memoryBytes;
        }
    }
    
    /**
     * Format bytes in human readable format
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    /**
     * Generate unique session ID
     */
    private String generateSessionId() {
        return "SYS-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
    
    /**
     * Check if monitoring is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Close monitor and cleanup resources
     */
    public void close() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Context object for tracking resource monitoring sessions
     */
    public static class ResourceMonitoringContext {
        private final long requestId;
        private final boolean monitoring;
        private final ScheduledFuture<?> scheduledTask;
        private final ExternalProcessMonitoringTask monitoringTask;
        
        public ResourceMonitoringContext(long requestId, boolean monitoring, 
                                       ScheduledFuture<?> scheduledTask, 
                                       ExternalProcessMonitoringTask monitoringTask) {
            this.requestId = requestId;
            this.monitoring = monitoring;
            this.scheduledTask = scheduledTask;
            this.monitoringTask = monitoringTask;
        }
        
        public long getRequestId() { return requestId; }
        public boolean isMonitoring() { return monitoring; }
        public ScheduledFuture<?> getScheduledTask() { return scheduledTask; }
        public ExternalProcessMonitoringTask getMonitoringTask() { return monitoringTask; }
    }
}
