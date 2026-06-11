package io.mist.core.multiservice;

import java.util.List; /** Represents a single parameter for a ValueGenerator (name and one or more values). */
public class GenParam {
    private String name;             // Name of the generator parameter (e.g., "min", "max", "type")
    private List<String> values;     // One or more values for this parameter (as strings for simplicity)
    // (Optionally, objectValues can be included for complex types; omitted for brevity)

    // Getters and setters...
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getValues() { return values; }
    public void setValues(List<String> values) { this.values = values; }
}
