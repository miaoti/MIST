# Smart Input Fetching System

## Overview

The **Smart Input Fetching System** is an advanced test data generation approach that intelligently fetches realistic values from existing APIs instead of generating random meaningless data. This system dramatically improves the quality of microservice testing by using actual system data.

## Problem Solved

### Before Smart Input Fetching
- LLM generates random station names like "RandomStation123" 
- API calls fail with 400 errors because data doesn't exist in system
- Tests don't reflect real-world scenarios
- Many false positives in test results

### After Smart Input Fetching  
- System fetches actual station names from `/api/v1/stationservice/stations`
- API calls succeed with realistic data
- Tests accurately simulate real user scenarios
- Higher quality test coverage with fewer false failures

## Key Features

🔍 **Intelligent API Discovery** - Maps input parameters to appropriate GET endpoints  
🧠 **LLM-Powered Analysis** - Uses AI to determine optimal data sources  
⚖️ **Configurable Mixing** - Balances realistic vs diverse data generation  
📚 **Learning Registry** - Caches successful mappings for performance  
🔄 **Dependency Resolution** - Handles complex parameter relationships  
💾 **Smart Caching** - Reduces API calls and improves speed  

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                Smart Input Fetching System                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────┐  ┌──────────────────┐  ┌─────────────┐ │
│  │   Parameter     │  │  LLM-Powered     │  │   Smart     │ │
│  │   Analysis      │→ │  API Discovery   │→ │  Fetching   │ │
│  └─────────────────┘  └──────────────────┘  └─────────────┘ │
│            │                    │                   │       │
│            ▼                    ▼                   ▼       │
│  ┌─────────────────┐  ┌──────────────────┐  ┌─────────────┐ │
│  │  Pattern-Based  │  │ Input Fetch      │  │    Cache    │ │
│  │   Discovery     │  │ Registry         │  │  Manager    │ │
│  └─────────────────┘  └──────────────────┘  └─────────────┘ │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│               Fallback to Traditional LLM                  │
└─────────────────────────────────────────────────────────────┘
```

## Configuration

### Properties File Configuration

Add these properties to your `trainticket-demo.properties`:

```properties
# Enable intelligent input fetching from existing APIs
smart.input.fetch.enabled=true

# Percentage of inputs to fetch from smart sources vs traditional LLM (0.0-1.0)
smart.input.fetch.percentage=0.3

# Path to the input fetch registry file
smart.input.fetch.registry.path=src/main/resources/My-Example/trainticket/input-fetch-registry.yaml

# Enable LLM-powered API discovery
smart.input.fetch.llm.discovery.enabled=true

# Maximum number of candidate APIs to consider per parameter
smart.input.fetch.max.candidates=5

# Enable dependency chain resolution
smart.input.fetch.dependency.resolution.enabled=true

# Timeout for API discovery requests (milliseconds)
smart.input.fetch.discovery.timeout=5000

# Enable caching of fetched values
smart.input.fetch.cache.enabled=true

# Cache TTL in seconds
smart.input.fetch.cache.ttl=300
```

### Registry File Structure

The system uses a YAML registry file to map parameters to data sources:

```yaml
# Smart Input Fetch Registry
version: "1.0"
lastUpdated: "2025-01-10T00:00:00Z"

parameterMappings:
  stationName:
    sourceApis:
      - endpoint: "/api/v1/stationservice/stations"
        method: "GET"
        service: "ts-station-service"
        extractPath: "$.data[*].name"
        priority: 10
        successRate: 0.95
        
  userId:
    sourceApis:
      - endpoint: "/api/v1/userservice/users"
        method: "GET" 
        service: "ts-user-service"
        extractPath: "$.data[*].userId"
        priority: 8

servicePatterns:
  - pattern: ".*[Ss]tation.*"
    services: ["ts-station-service"]
    endpoints: ["/api/v1/stationservice/stations"]
    
  - pattern: ".*[Uu]ser.*"
    services: ["ts-user-service", "ts-contacts-service"]
    endpoints: ["/api/v1/userservice/users", "/api/v1/contactservice/contacts"]
```

## Usage Examples

### Basic Usage

1. **Enable the system** in your properties file:
   ```properties
   smart.input.fetch.enabled=true
   smart.input.fetch.percentage=0.3
   ```

2. **Run your tests** - the system will automatically:
   - Analyze parameter names (e.g., "stationName")
   - Discover appropriate APIs (e.g., station service)
   - Fetch realistic values (e.g., "Beijing Station")
   - Cache results for performance
   - Fall back to LLM if needed

### Advanced Configuration

**Custom API Mappings**: Add your own parameter mappings to the registry:

```yaml
parameterMappings:
  customField:
    sourceApis:
      - endpoint: "/api/v1/myservice/data"
        method: "GET"
        service: "my-custom-service"
        extractPath: "$.items[*].customField"
        priority: 9
```

**Service Patterns**: Define patterns for automatic discovery:

```yaml
servicePatterns:
  - pattern: ".*[Pp]roduct.*"
    services: ["ts-product-service"]
    endpoints: ["/api/v1/productservice/products"]
```

## Integration

### With MultiServiceTestCaseGenerator

The system automatically integrates with the existing MST workflow:

```java
// The system is automatically initialized when MST mode is used
// No code changes required - just enable via properties

// Smart fetching happens transparently during parameter generation:
// 1. Check cache for existing values
// 2. Try smart fetching from mapped APIs
// 3. Fall back to traditional LLM generation
// 4. Cache successful results
```

### With Custom Generators

You can also use the Smart Input Fetching system directly:

```java
import es.us.isa.restest.inputs.smart.SmartLLMParameterGenerator;

// Use SmartLLMParameterGenerator instead of LLMParameterGenerator
SmartLLMParameterGenerator generator = new SmartLLMParameterGenerator();
String value = generator.nextValueAsString();
```

## API Discovery Process

The system uses a multi-stage discovery process:

### 1. Cache Check
```
Parameter: "stationName" → Cache: "Beijing Station" ✓
```

### 2. Registry Lookup  
```
Parameter: "stationName" → Registry: "/api/v1/stationservice/stations" ✓
```

### 3. Pattern Matching
```
Parameter: "newStationId" → Pattern: ".*[Ss]tation.*" → Services: ["ts-station-service"] ✓
```

### 4. LLM Discovery
```
Parameter: "unknownField" → LLM Analysis → Suggested Services: ["ts-data-service"] ✓
```

### 5. Fallback
```
All smart sources failed → Traditional LLM Generation ✓
```

## Performance Optimizations

- **Caching**: Frequently used values are cached to reduce API calls
- **Connection Pooling**: HTTP connections are reused when possible  
- **Timeout Controls**: Configurable timeouts prevent hanging requests
- **Prioritization**: Higher priority APIs are tried first
- **Success Rate Learning**: System learns which APIs work best

## Monitoring and Debugging

### Log Levels

Enable debug logging to see the system in action:

```properties
logging.level.es.us.isa.restest.inputs.smart=DEBUG
```

### Log Examples

```
INFO  Smart Fetch → ts-station-service stationName = Beijing Station (step 1)
DEBUG Using cached value for parameter 'stationId'
WARN  Smart fetching failed for stationName, falling back to LLM: HTTP 404
DEBUG Pattern-discovered mapping: routeId -> ApiMapping{endpoint='/api/v1/routeservice/routes'}
```

### Registry Updates

The system automatically learns and updates the registry:

```yaml
stationName:
  sourceApis:
    - endpoint: "/api/v1/stationservice/stations"
      successRate: 0.87  # Updated based on actual usage
      lastUsed: "2025-01-10T14:30:00Z"  # Updated timestamp
```

## Advanced Features

### Dependency Chain Resolution

When enabled, the system can handle complex parameter relationships:

```yaml
# If orderId depends on userId, the system can:
# 1. Fetch userId from user service
# 2. Use that userId to fetch relevant orderId from order service
# 3. Maintain data consistency across the test scenario
```

### Custom Data Extraction

Define custom JSONPath expressions for complex API responses:

```yaml
extractPath: "$.result.data[?(@.active == true)].stationName"
```

### Error Handling

The system gracefully handles various error conditions:

- **Network timeouts**: Falls back to LLM after timeout
- **Invalid responses**: Tries next API mapping in priority order
- **Empty results**: Uses LLM generation as fallback
- **Service unavailable**: Caches failure and tries alternatives

## Benefits

### For Developers
- ✅ **Realistic Test Data**: Tests use actual system values
- ✅ **Reduced False Positives**: Fewer tests fail due to invalid data
- ✅ **Better Coverage**: Tests cover real-world scenarios
- ✅ **Faster Debugging**: Issues are easier to reproduce and fix

### For QA Teams  
- ✅ **Higher Quality Tests**: Tests reflect actual user journeys
- ✅ **Edge Case Discovery**: Real data exposes edge cases
- ✅ **Consistent Results**: Cacheable, repeatable test data
- ✅ **Reduced Maintenance**: Self-learning system reduces manual updates

### For DevOps
- ✅ **Faster CI/CD**: Fewer test failures = faster builds
- ✅ **Better Monitoring**: Tests reveal actual system behavior
- ✅ **Resource Efficiency**: Cached data reduces API load
- ✅ **Scalable Approach**: System learns and improves over time

## Troubleshooting

### Common Issues

**Q: Smart fetching is not working**
A: Check that `smart.input.fetch.enabled=true` and the base URL is correct

**Q: Getting HTTP 404 errors**  
A: Verify the API endpoints exist and the service is running

**Q: Performance is slow**
A: Enable caching with `smart.input.fetch.cache.enabled=true`

**Q: Registry file not found**
A: Check the `smart.input.fetch.registry.path` property and file permissions

### Debug Checklist

1. ✅ Verify configuration properties are loaded
2. ✅ Check base URL connectivity  
3. ✅ Confirm API endpoints are available
4. ✅ Review registry file format and content
5. ✅ Enable debug logging for detailed information
6. ✅ Check system properties and environment variables

## Contributing

To extend the Smart Input Fetching System:

1. **Add new service patterns** to the registry file
2. **Implement custom discovery logic** in `SmartInputFetcher`
3. **Create specialized extractors** for complex API responses
4. **Add new cache strategies** for improved performance

## Future Enhancements

- 🚀 **GraphQL Support**: Extend to GraphQL APIs
- 🚀 **Machine Learning**: ML-based parameter matching
- 🚀 **Real-time Updates**: Live registry updates from API changes
- 🚀 **Multi-tenancy**: Support for different environments/tenants
- 🚀 **Metrics Dashboard**: Visual monitoring of system performance

---

**Smart Input Fetching System** - Making test data as realistic as your production environment! 🎯 