package io.mist.core.enhancer;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Snapshot of a parameter's state at the time of test execution.
 * Used by the Test Case Enhancer to provide context to the LLM.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParameterSnapshot {
    
    private String name;
    private String value;
    private String type;
    private String location;  // path, query, header, body
    private String description;
    private String example;
    private String format;
    private boolean required;
    private int stepIndex;
    private boolean dataInjected;
    
    // Default constructor for JSON deserialization
    public ParameterSnapshot() {}
    
    public ParameterSnapshot(String name, String value, String type, String location) {
        this.name = name;
        this.value = value;
        this.type = type;
        this.location = location;
    }
    
    // Builder pattern for fluent construction
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final ParameterSnapshot snapshot = new ParameterSnapshot();
        
        public Builder name(String name) {
            snapshot.name = name;
            return this;
        }
        
        public Builder value(String value) {
            snapshot.value = value;
            return this;
        }
        
        public Builder type(String type) {
            snapshot.type = type;
            return this;
        }
        
        public Builder location(String location) {
            snapshot.location = location;
            return this;
        }
        
        public Builder description(String description) {
            snapshot.description = description;
            return this;
        }
        
        public Builder example(String example) {
            snapshot.example = example;
            return this;
        }
        
        public Builder format(String format) {
            snapshot.format = format;
            return this;
        }
        
        public Builder required(boolean required) {
            snapshot.required = required;
            return this;
        }
        
        public Builder stepIndex(int stepIndex) {
            snapshot.stepIndex = stepIndex;
            return this;
        }
        
        public Builder dataInjected(boolean dataInjected) {
            snapshot.dataInjected = dataInjected;
            return this;
        }
        
        public ParameterSnapshot build() {
            return snapshot;
        }
    }
    
    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getExample() { return example; }
    public void setExample(String example) { this.example = example; }
    
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    
    public int getStepIndex() { return stepIndex; }
    public void setStepIndex(int stepIndex) { this.stepIndex = stepIndex; }
    
    public boolean isDataInjected() { return dataInjected; }
    public void setDataInjected(boolean dataInjected) { this.dataInjected = dataInjected; }
    
    @Override
    public String toString() {
        return "ParameterSnapshot{" +
                "name='" + name + '\'' +
                ", value='" + value + '\'' +
                ", type='" + type + '\'' +
                ", location='" + location + '\'' +
                ", stepIndex=" + stepIndex +
                ", dataInjected=" + dataInjected +
                '}';
    }
}




