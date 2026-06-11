package io.mist.core.util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converter to parse injected faults from markdown documentation and convert to JSON format.
 * Extracts fault name and API endpoint information from INJECTED_FAULTS.md file.
 */
public class InjectedFaultConverter {
    private static final Logger log = LogManager.getLogger(InjectedFaultConverter.class);
    
    // Pattern to match fault heading: ### 1. FAULT_NAME or ### 1. FAULT_NAME (Description)
    private static final Pattern FAULT_HEADING_PATTERN = Pattern.compile("^###\\s+\\d+\\.\\s+(.+)$");
    
    // Pattern to match API line: **API:** `POST /api/v1/...` or **API:** `GET /api/v1/...`
    private static final Pattern API_PATTERN = Pattern.compile("^\\*\\*API:\\*\\*\\s+`(.+?)`$");
    
    /**
     * Represents a single injected fault with its name and API endpoint.
     */
    public static class InjectedFault {
        private String faultName;
        private String api;
        private String service;
        
        public InjectedFault(String faultName, String api, String service) {
            this.faultName = faultName;
            this.api = api;
            this.service = service;
        }
        
        public String getFaultName() {
            return faultName;
        }
        
        public String getApi() {
            return api;
        }
        
        public String getService() {
            return service;
        }
        
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("faultName", faultName);
            json.put("api", api);
            json.put("service", service);
            return json;
        }
        
        @Override
        public String toString() {
            return String.format("InjectedFault{faultName='%s', api='%s', service='%s'}", 
                               faultName, api, service);
        }
    }
    
    /**
     * Parses the injected faults markdown file and extracts fault information.
     * 
     * @param markdownFilePath path to the INJECTED_FAULTS.md file
     * @return list of extracted injected faults
     * @throws IOException if file cannot be read
     */
    public List<InjectedFault> parseMarkdownFile(String markdownFilePath) throws IOException {
        List<InjectedFault> faults = new ArrayList<>();
        
        log.info("Reading markdown file: {}", markdownFilePath);
        List<String> lines = Files.readAllLines(Paths.get(markdownFilePath), StandardCharsets.UTF_8);
        
        String currentFaultName = null;
        String currentService = null;
        
        for (String line : lines) {
            line = line.trim();
            
            // Check for service section (## service-name)
            if (line.startsWith("## ") && !line.equals("## Injected Faults Documentation")) {
                currentService = line.substring(3).trim();
                log.debug("Found service section: {}", currentService);
                continue;
            }
            
            // Check for fault heading
            Matcher faultMatcher = FAULT_HEADING_PATTERN.matcher(line);
            if (faultMatcher.matches()) {
                currentFaultName = faultMatcher.group(1).trim();
                log.debug("Found fault: {}", currentFaultName);
                continue;
            }
            
            // Check for API line
            Matcher apiMatcher = API_PATTERN.matcher(line);
            if (apiMatcher.matches() && currentFaultName != null) {
                String api = apiMatcher.group(1).trim();
                InjectedFault fault = new InjectedFault(currentFaultName, api, currentService);
                faults.add(fault);
                log.info("Extracted fault: {} -> {}", currentFaultName, api);
                
                // Reset current fault name after extraction
                currentFaultName = null;
            }
        }
        
        log.info("Total faults extracted: {}", faults.size());
        return faults;
    }
    
    /**
     * Converts list of injected faults to JSON format and saves to file.
     * 
     * @param faults list of injected faults
     * @param outputFilePath path where JSON file should be saved
     * @throws IOException if file cannot be written
     */
    public void saveToJson(List<InjectedFault> faults, String outputFilePath) throws IOException {
        JSONObject root = new JSONObject();
        
        // Add metadata
        JSONObject metadata = new JSONObject();
        metadata.put("total_faults", faults.size());
        metadata.put("generated_at", new java.util.Date().toString());
        metadata.put("version", "1.0");
        root.put("metadata", metadata);
        
        // Add faults array
        JSONArray faultsArray = new JSONArray();
        for (InjectedFault fault : faults) {
            faultsArray.put(fault.toJson());
        }
        root.put("injected_faults", faultsArray);
        
        // Write to file with pretty formatting
        File file = new File(outputFilePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write(root.toString(2)); // 2 spaces indentation
        }
        
        log.info("JSON file saved to: {}", outputFilePath);
    }
    
    /**
     * Main conversion method that reads markdown file and outputs JSON.
     * 
     * @param markdownFilePath path to input markdown file
     * @return path to generated JSON file
     * @throws IOException if file operations fail
     */
    public String convert(String markdownFilePath) throws IOException {
        // Parse markdown file
        List<InjectedFault> faults = parseMarkdownFile(markdownFilePath);
        
        // Generate output file path in the same directory
        File inputFile = new File(markdownFilePath);
        String directory = inputFile.getParent();
        String outputFileName = "injected-faults.json";
        String outputFilePath = directory != null ? 
            directory + File.separator + outputFileName : outputFileName;
        
        // Save to JSON
        saveToJson(faults, outputFilePath);
        
        return outputFilePath;
    }
}

