# Fault Detection Tracking Implementation

## Overview
This implementation adds comprehensive fault detection tracking to the MST (Multi-Service Testing) mode of RESTest. The system monitors root API responses for injected faults during test execution and generates detailed coverage reports.

## Implementation Details

### 1. FaultDetectionTracker Class
**Location**: `src/main/java/es/us/isa/restest/analysis/FaultDetectionTracker.java`

A thread-safe singleton tracker that:
- Loads injected faults from `injected-faults.json` registry
- Records detected faults during test execution
- Tracks which test cases detected which faults
- Generates comprehensive fault coverage reports

**Key Features**:
- Thread-safe using `ConcurrentHashMap` for parallel test execution
- Singleton pattern ensures single tracker instance across all tests
- Detailed statistics tracking (detection rate, test counts, timestamps)
- Beautiful ASCII report with progress bars and clear formatting

### 2. Test Code Generation Enhancement
**Location**: `src/main/java/es/us/isa/restest/writers/restassured/MultiServiceRESTAssuredWriter.java`

**Lines Modified**: Around line 1149-1181

The writer now injects fault detection code into generated tests:
- Only checks root API responses (step 0)
- Parses response JSON for `data.isInjected` and `data.faultName`
- Records detections using `FaultDetectionTracker.getInstance()`
- Silent fail pattern - doesn't break test execution if detection fails
- Console logging for immediate feedback

**Generated Code Pattern**:
```java
// 🔍 FAULT DETECTION: Check if root API response contains injected fault
try {
    String faultCheckBody = stepResponse0.getBody().asString();
    org.json.JSONObject faultJson = new org.json.JSONObject(faultCheckBody);
    if (faultJson.has("data")) {
        org.json.JSONObject dataObj = faultJson.getJSONObject("data");
        if (dataObj.optBoolean("isInjected", false)) {
            String detectedFaultName = dataObj.optString("faultName", "");
            if (!detectedFaultName.isEmpty()) {
                FaultDetectionTracker.getInstance().recordDetectedFault(
                    detectedFaultName,
                    this.getClass().getName(),
                    "testMethodName",
                    System.currentTimeMillis(),
                    faultCheckBody
                );
                System.out.println("🔍 FAULT DETECTED: " + detectedFaultName);
            }
        }
    }
} catch (Exception faultEx) {
    // Silent fail - don't break test execution
}
```

### 3. Integration with TestGenerationAndExecution
**Location**: `src/main/java/es/us/isa/restest/main/TestGenerationAndExecution.java`

**Initialization** (Lines 172-181):
- Loads injected faults registry before test execution
- Resets tracker state for each test run
- Configurable via properties file

**Report Generation** (Lines 221-237):
- Generates report after all tests complete
- Logs statistics to console
- Creates timestamped report file

### 4. Configuration
**Location**: `src/main/resources/My-Example/trainticket-demo.properties`

New properties added:
```properties
# FAULT DETECTION TRACKING CONFIGURATION
fault.detection.enabled=true
fault.detection.injected.faults.path=src/main/resources/My-Example/trainticket/injectedFaults/injected-faults.json
fault.detection.report.dir=logs/fault-detection-reports
```

## Report Format

Reports are saved to: `logs/fault-detection-reports/fault-detection-summary-[experimentName]-[timestamp].txt`

### Report Structure:
```
================================================================================
                    FAULT DETECTION SUMMARY REPORT
================================================================================

Experiment:         trainticket_twostage_test_20251028143000
Generated:          2025-10-28 14:35:00
Tracking Started:   2025-10-28 14:30:00
Total Test Cases:   50

================================================================================
FAULT COVERAGE SUMMARY
================================================================================

Total Injected Faults:    16
Detected Faults:          12 (75.0%)
Undetected Faults:        4 (25.0%)

Detection Progress:
[█████████████████████████████████████░░░░░░░░░░░░░] 75.0%

================================================================================
DETECTED FAULTS (12)
================================================================================

1. INVALID_CONTACTS_NAME_FAULT
   Service:       ts-admin-order-service
   API:           POST /api/v1/adminorderservice/adminorder
   Detections:    2 time(s)

   Detection #1:
     Test Class:  trainticket_twostage_test.TrainTicketTest_20251028143000
     Test Method: AdminOrderService_CreateOrder_variant_0_test
     Timestamp:   2025-10-28 14:32:15

   Detection #2:
     Test Class:  trainticket_twostage_test.TrainTicketTest_20251028143000
     Test Method: AdminOrderService_CreateOrder_variant_1_test
     Timestamp:   2025-10-28 14:32:45

--------------------------------------------------------------------------------

2. INVALID_SEAT_NUMBER_FAULT
   ...

================================================================================
UNDETECTED FAULTS (4)
================================================================================

1. INSUFFICIENT_STATIONS_FAULT
   Service:       ts-admin-route-service
   API:           POST /api/v1/adminrouteservice/adminroute

2. DUPLICATE_STATIONS_FAULT
   ...

================================================================================
TEST CASES EXECUTED (50)
================================================================================

1. trainticket_twostage_test.TrainTicketTest_20251028143000.test_1
2. trainticket_twostage_test.TrainTicketTest_20251028143000.test_2
...

================================================================================
                          END OF REPORT
================================================================================
```

## Key Features

### 1. Thread Safety
- Uses `ConcurrentHashMap` for thread-safe tracking
- Synchronized methods for critical operations
- Supports parallel test execution

### 2. Fault Detection Logic
- Only monitors root API responses (step 0)
- Checks for `data.isInjected == true` in JSON response
- Extracts `data.faultName` for tracking
- Silent fail pattern - never breaks test execution

### 3. Comprehensive Reporting
- Percentage-based coverage metrics
- Detailed detection history with timestamps
- Test case attribution (which test detected which fault)
- Multiple detections per fault tracked
- Visual progress bar for quick assessment

### 4. Integration Points
- Automatic initialization in MST mode
- Report generation after test execution
- Console logging for immediate feedback
- Configurable via properties file

## Usage

### Running MST Mode with Fault Detection
1. Ensure `fault.detection.enabled=true` in properties file
2. Run test generation and execution normally
3. Check console for detection logs: `🔍 FAULT DETECTED: [faultName]`
4. After execution completes, find report in `logs/fault-detection-reports/`

### Viewing Reports
Reports are timestamped and saved permanently:
- Each run creates a new report file
- Previous reports are preserved
- Easy comparison across multiple test runs

### Console Output
During execution, you'll see:
```
🔍 Initializing Fault Detection Tracker...
🔍 Fault Detection Tracker initialized from: src/.../injected-faults.json
...
🔍 FAULT DETECTED: INVALID_CONTACTS_NAME_FAULT in test: AdminOrderService_test
...
🔍 Generating Fault Detection Report...
🔍 Fault Detection Summary:
   - Total Injected Faults: 16
   - Detected Faults: 12
   - Detection Rate: 75.0%
   - Report saved to: logs/fault-detection-reports
```

## Architecture Benefits

1. **Non-Invasive**: Detection code is injected during code generation, not runtime
2. **Fault Tolerant**: Detection failures never break test execution
3. **Scalable**: Thread-safe design supports parallel execution
4. **Maintainable**: Clean separation of concerns with dedicated tracker class
5. **Observable**: Rich logging and reporting for monitoring

## File Structure
```
Rest/
├── src/main/java/es/us/isa/restest/
│   ├── analysis/
│   │   └── FaultDetectionTracker.java          [NEW]
│   ├── writers/restassured/
│   │   └── MultiServiceRESTAssuredWriter.java  [MODIFIED]
│   └── main/
│       └── TestGenerationAndExecution.java     [MODIFIED]
├── src/main/resources/My-Example/
│   └── trainticket-demo.properties             [MODIFIED]
└── logs/
    └── fault-detection-reports/                [NEW]
        └── fault-detection-summary-*.txt
```

## Testing Verification

To verify the implementation:
1. Run MST mode: `mvn test` or execute `TestGenerationAndExecution.main()`
2. Check console for `🔍 FAULT DETECTED:` messages
3. Verify report generation in `logs/fault-detection-reports/`
4. Confirm report contains:
   - Correct total fault count (16 for TrainTicket)
   - Detected faults with test case details
   - Undetected faults list
   - Accurate detection percentage

## Future Enhancements

Possible improvements:
1. Add fault detection to Allure reports
2. Track fault detection trends across multiple runs
3. Generate fault detection graphs/charts
4. Export reports to JSON/CSV for analysis
5. Add fault priority/severity tracking
6. Implement fault detection filters by service/API

