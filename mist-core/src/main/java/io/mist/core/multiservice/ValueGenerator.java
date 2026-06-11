package io.mist.core.multiservice;

import java.util.List;

/** Represents a test data generator for parameter values (e.g., RandomNumber, RandomString). */
public class ValueGenerator {
    private String type;                       // Generator type (e.g., "RandomNumber", "RandomString")
    private List<GenParam> genParameters;      // Configuration parameters for the generator
    private  String description;

    // Getters and setters...
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public List<GenParam> getGenParameters() { return genParameters; }
    public void setGenParameters(List<GenParam> genParameters) { this.genParameters = genParameters; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

