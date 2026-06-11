package io.mist.core.spec;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Represents an OAS parameter for easier usage in RESTest.
 */
public class OpenAPIParameter {

    private String name;
    private String in;
    private String type;
    private String format;
    private String pattern;
    private String description;
    private List<String> enumValues;
    private BigDecimal min;
    private BigDecimal max;
    private Integer minLength;
    private Integer maxLength;
    private Boolean required;
    private Schema<?> schema;

    // Store example from parameter or schema
    private Object example;

    /**
     * Constructor for path/query/header parameters, using a fully defined OAS Parameter object.
     */
    public OpenAPIParameter(Parameter p) {
        name = p.getName();
        in = p.getIn();
        type = p.getSchema().getType();
        format = p.getSchema().getFormat();
        pattern = p.getSchema().getPattern();
        enumValues = p.getSchema().getEnum();
        description = p.getDescription();

        if ("array".equals(type)) {
            enumValues = (List<String>) ((ArraySchema) p.getSchema()).getItems().getEnum();
        }

        min = p.getSchema().getMinimum();
        max = p.getSchema().getMaximum();
        minLength = p.getSchema().getMinLength();
        maxLength = p.getSchema().getMaxLength();
        required = (p.getRequired() == null ? Boolean.FALSE : p.getRequired());

        // Retrieve parameter-level example first
        this.example = p.getExample();

        // If still null, check the schema-level example
        if (this.example == null && p.getSchema() != null) {
            this.example = p.getSchema().getExample();
        }
    }

    /**
     * Constructor for body parameters (just "body", "body", required).
     * Used by findBodyParameterFeatures in OpenAPISpecificationVisitor.
     */
    public OpenAPIParameter(String name, String in, Boolean required) {
        this.name = name;
        this.in = in;
        this.required = (required != null) ? required : Boolean.FALSE;

        // No direct schema here, so we leave type, format, etc. null
        this.type = null;
        this.format = null;
        this.pattern = null;
        this.enumValues = null;
        this.min = null;
        this.max = null;
        this.minLength = null;
        this.maxLength = null;
        this.description = null;

        // By default, no example
        this.example = null;
    }

    /**
     * Constructor for formData parameters
     * (e.g., new OpenAPIParameter(key, schema, required)).
     */
    public OpenAPIParameter(String name, Schema s, Boolean required) {
        this.name = name;
        this.in = "formData";
        this.required = (required != null) ? required : Boolean.FALSE;

        this.type = s.getType();
        this.format = s.getFormat();
        this.pattern = s.getPattern();
        // 🔥 FIX: Extract description from schema
        this.description = s.getDescription();
        this.enumValues = s.getEnum();
        if ("array".equals(type)) {
            this.enumValues = (List<String>) ((ArraySchema) s).getItems().getEnum();
        }
        this.min = s.getMinimum();
        this.max = s.getMaximum();
        this.minLength = s.getMinLength();
        this.maxLength = s.getMaxLength();

        // 🔥 FIX: If there's an example in the schema, preserve it as string if it's a date format
        // to avoid Date object serialization (e.g., '2025-11-01' -> 2025-11-01T00:00:00Z)
        Object exampleValue = s.getExample();
        if (exampleValue != null && "date".equals(this.format) && exampleValue instanceof java.util.Date) {
            // Convert Date object back to simple date string format
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            this.example = sdf.format((java.util.Date) exampleValue);
        } else {
            this.example = exampleValue;
        }
    }

    // ---------- GETTERS / SETTERS ----------

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }


    public String getDescription(){
        return description;
    }
    public void setDescription(String description){ this.description = description; }

    public String getIn() { return in; }
    public void setIn(String in) { this.in = in; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public List<String> getEnumValues() { return enumValues; }
    public void setEnumValues(List<String> enumValues) { this.enumValues = enumValues; }

    public BigDecimal getMin() { return min; }
    public void setMin(BigDecimal min) { this.min = min; }

    public BigDecimal getMax() { return max; }
    public void setMax(BigDecimal max) { this.max = max; }

    public Integer getMinLength() { return minLength; }
    public void setMinLength(Integer minLength) { this.minLength = minLength; }

    public Integer getMaxLength() { return maxLength; }
    public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }

    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }

    public Object getExample() {
        if (example != null) {
            return example;
        }
        if (schema != null) {
            return schema.getExample();
        }
        return null;
    }
    public void setExample(Object example) { this.example = example; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OpenAPIParameter)) return false;
        OpenAPIParameter that = (OpenAPIParameter) o;
        return Objects.equals(name, that.name)
                && Objects.equals(in, that.in)
                && Objects.equals(type, that.type)
                && Objects.equals(format, that.format)
                && Objects.equals(pattern, that.pattern)
                && Objects.equals(enumValues, that.enumValues)
                && Objects.equals(min, that.min)
                && Objects.equals(max, that.max)
                && Objects.equals(minLength, that.minLength)
                && Objects.equals(maxLength, that.maxLength)
                && Objects.equals(required, that.required)
                && Objects.equals(example, that.example);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, in, type, format, pattern, enumValues, min, max, minLength, maxLength, required, example);
    }

    public String getSchemaType() {
        if (schema != null && schema.getType() != null) {
            return schema.getType();
        }
        return null;
    }
}
