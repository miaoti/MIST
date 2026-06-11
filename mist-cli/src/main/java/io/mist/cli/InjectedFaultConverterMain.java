package io.mist.cli;

import io.mist.core.util.InjectedFaultConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * Main class to run the Injected Fault Converter.
 * Converts INJECTED_FAULTS.md to JSON format containing fault names and their APIs.
 * 
 * Usage: Update the MARKDOWN_FILE_PATH constant with your file path and run this class.
 */
public class InjectedFaultConverterMain {
    private static final Logger logger = LogManager.getLogger(InjectedFaultConverterMain.class);
    
    // ============================================================
    // CONFIGURATION: Update this path to your markdown file
    // ============================================================
    private static final String MARKDOWN_FILE_PATH = 
        "src/main/resources/My-Example/trainticket/injectedFaults/INJECTED_FAULTS.md";
    
    public static void main(String[] args) {
        logger.info("=".repeat(70));
        logger.info("Injected Fault Converter");
        logger.info("=".repeat(70));
        logger.info("");
        
        try {
            // Validate input file exists
            File inputFile = new File(MARKDOWN_FILE_PATH);
            if (!inputFile.exists()) {
                logger.error("ERROR: Input file not found: {}", MARKDOWN_FILE_PATH);
                logger.error("Please update the MARKDOWN_FILE_PATH constant in this class.");
                System.exit(1);
            }
            
            logger.info("Input file: {}", inputFile.getAbsolutePath());
            logger.info("File size: {} bytes", inputFile.length());
            logger.info("");
            
            // Create converter and process
            InjectedFaultConverter converter = new InjectedFaultConverter();
            
            logger.info("Starting conversion...");
            String outputFilePath = converter.convert(MARKDOWN_FILE_PATH);
            
            // Display results
            File outputFile = new File(outputFilePath);
            logger.info("");
            logger.info("=".repeat(70));
            logger.info("✓ Conversion completed successfully!");
            logger.info("=".repeat(70));
            logger.info("Output file: {}", outputFile.getAbsolutePath());
            logger.info("Output file size: {} bytes", outputFile.length());
            logger.info("");
            logger.info("The JSON file has been saved in the same folder as the markdown file.");
            logger.info("");
            
        } catch (Exception e) {
            logger.error("=".repeat(70));
            logger.error("✗ Conversion failed!");
            logger.error("=".repeat(70));
            logger.error("Error: {}", e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

