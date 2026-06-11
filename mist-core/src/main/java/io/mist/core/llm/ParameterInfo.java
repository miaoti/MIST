package io.mist.core.llm;

import java.util.List;

public class ParameterInfo {
    private String name;
    private String description;
    private String type;
    private String inLocation;
    private String format;
    private String regex;

    private String schemaType;
    private String schemaExample;
    private Boolean required;

    // OpenAPI constraint fields — carried from TestParameter for prompt enrichment
    private List<String> enumValues;
    private Number minimum;
    private Number maximum;
    private Integer minLength;
    private Integer maxLength;

    // Additional context for better LLM generation
    private String apiName;           // e.g., "POST /api/v1/adminorder"
    private String serviceName;       // e.g., "ts-admin-order-service"
    private List<String> allParameterNames;  // All parameters in this API for context

    // Trace-aware context: endpoints observed as producers in the original workflow
    private List<String> traceProducerEndpoints;

    // Getters / Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getInLocation() { return inLocation; }
    public void setInLocation(String inLocation) { this.inLocation = inLocation; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getRegex() { return regex; }
    public void setRegex(String regex) { this.regex = regex; }

    public String getSchemaType() { return schemaType; }
    public void setSchemaType(String schemaType) { this.schemaType = schemaType; }
    public String getSchemaExample() { return schemaExample; }
    public void setSchemaExample(String schemaExample) { this.schemaExample = schemaExample; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }

    public List<String> getEnumValues() { return enumValues; }
    public void setEnumValues(List<String> enumValues) { this.enumValues = enumValues; }
    public Number getMinimum() { return minimum; }
    public void setMinimum(Number minimum) { this.minimum = minimum; }
    public Number getMaximum() { return maximum; }
    public void setMaximum(Number maximum) { this.maximum = maximum; }
    public Integer getMinLength() { return minLength; }
    public void setMinLength(Integer minLength) { this.minLength = minLength; }
    public Integer getMaxLength() { return maxLength; }
    public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }

    public String getApiName() { return apiName; }
    public void setApiName(String apiName) { this.apiName = apiName; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public List<String> getAllParameterNames() { return allParameterNames; }
    public void setAllParameterNames(List<String> allParameterNames) { this.allParameterNames = allParameterNames; }

    public List<String> getTraceProducerEndpoints() { return traceProducerEndpoints; }
    public void setTraceProducerEndpoints(List<String> traceProducerEndpoints) { this.traceProducerEndpoints = traceProducerEndpoints; }

    /** Returns true if this parameter has an explicit enum constraint. */
    public boolean hasEnum() {
        return enumValues != null && !enumValues.isEmpty();
    }

    /** Returns true if any numeric boundary (min or max) is set. */
    public boolean hasBounds() {
        return minimum != null || maximum != null;
    }

    /** Returns true if any string-length constraint is set. */
    public boolean hasLengthConstraints() {
        return minLength != null || maxLength != null;
    }

    @Override
    public String toString() {
        return "ParameterInfo{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", format='" + format + '\'' +
                ", inLocation='" + inLocation + '\'' +
                ", regex='" + regex + '\'' +
                ", description='" + description + '\'' +
                ", enumValues=" + enumValues +
                ", minimum=" + minimum +
                ", maximum=" + maximum +
                ", minLength=" + minLength +
                ", maxLength=" + maxLength +
                ", schemaExample='" + schemaExample + '\'' +
                ", required=" + required +
                ", apiName='" + apiName + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", allParameterNames=" + allParameterNames +
                ", traceProducerEndpoints=" + traceProducerEndpoints +
                '}';
    }
}
