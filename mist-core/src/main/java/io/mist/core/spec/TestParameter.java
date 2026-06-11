package io.mist.core.spec;

import java.util.List;

public class TestParameter {
    private String name;
    private String in;
    private Float weight;
    private List<Generator> generators;
    private String description;

    // ── new fields ──────────────────────────────────
    private String type;
    private String format;
    private String pattern;
    private List<String> enumValues;
    private Number minimum;
    private Number maximum;
    private Integer minLength;
    private Integer maxLength;
    private Object example;
    private Boolean required;
    // ────────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIn() { return in; }
    public void setIn(String in) { this.in = in; }

    public Float getWeight() { return weight; }
    public void setWeight(Float weight) { this.weight = weight; }

    public List<Generator> getGenerators() { return generators; }
    public void setGenerators(List<Generator> generators) { this.generators = generators; }

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

    public Number getMinimum() { return minimum; }
    public void setMinimum(Number minimum) { this.minimum = minimum; }

    public Number getMaximum() { return maximum; }
    public void setMaximum(Number maximum) { this.maximum = maximum; }

    public Integer getMinLength() { return minLength; }
    public void setMinLength(Integer minLength) { this.minLength = minLength; }

    public Integer getMaxLength() { return maxLength; }
    public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }

    public Object getExample() { return example; }
    public void setExample(Object example) { this.example = example; }

    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
    // ────────────────────────────────────────────────
}
