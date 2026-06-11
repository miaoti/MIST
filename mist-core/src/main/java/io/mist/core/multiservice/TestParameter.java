package io.mist.core.multiservice;

import java.math.BigDecimal;
import java.util.List;

public class TestParameter {
    private String name;
    private String in;
    private Double weight;
    private List<ValueGenerator> generators;
    private boolean valid;
    private String description;

    // ── new fields ──────────────────────────────────
    private String type;
    private String format;
    private String pattern;
    private List<String> enumValues;
    private BigDecimal min;
    private BigDecimal max;
    private Integer minLength;
    private Integer maxLength;
    private Object example;
    private Boolean required;
    // ────────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIn() { return in; }
    public void setIn(String in) { this.in = in; }
    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    // ── new getters/setters ─────────────────────────
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

    public Object getExample() { return example; }
    public void setExample(Object example) { this.example = example; }

    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }

    public void setGenerators(List<ValueGenerator> generators) {
        this.generators = generators;
    }

    public ValueGenerator[] getGenerators() {
        return generators.toArray(new ValueGenerator[generators.size()]);
    }
    // ────────────────────────────────────────────────
}
