package io.mist.core.registry;

import org.json.JSONArray;
import org.json.JSONObject;
import io.mist.core.workflow.WorkflowStep;

/**
 * Represents a specific tree structure (execution pattern) for a root API.
 * Each tree captures the hierarchical microservice interaction pattern
 * observed from a particular trace execution.
 * 
 * The tree structure shows:
 * - Which services are called
 * - In what hierarchical order
 * - The API operations invoked
 * 
 * This class focuses on the structural pattern rather than specific data values,
 * making it useful for understanding microservice interaction patterns.
 */
public class ApiTree {
    private final String treeId;
    private final String sourceTrace;
    private final TreeNode root;
    
    /**
     * Creates a new ApiTree from a WorkflowStep tree.
     */
    public ApiTree(String treeId, String sourceTrace, WorkflowStep rootStep, String unused) {
        this.treeId = treeId;
        this.sourceTrace = sourceTrace;
        this.root = convertWorkflowStepToTreeNode(rootStep);
    }
    
    /**
     * Private constructor for deserialization.
     */
    private ApiTree(String treeId, String sourceTrace, TreeNode root) {
        this.treeId = treeId;
        this.sourceTrace = sourceTrace;
        this.root = root;
    }
    
    /**
     * Converts a WorkflowStep tree to a simplified TreeNode structure.
     * This focuses on the API call patterns rather than specific execution data.
     * Filters out gateway calls and internal operations, keeping only actual API calls.
     */
    private TreeNode convertWorkflowStepToTreeNode(WorkflowStep step) {
        String httpMethod = extractHttpMethod(step);
        String apiPath = extractApiPath(step);
        
        TreeNode node = new TreeNode(
            step.getServiceName(),
            step.getOperationName(),
            httpMethod,
            apiPath
        );
        
        // Convert children, filtering out gateway and internal calls
        for (WorkflowStep child : step.getChildren()) {
            TreeNode childNode = convertWorkflowStepToTreeNode(child);
            // Only add if it's an actual API call (not gateway, not internal operation)
            if (isActualApiCall(childNode)) {
                node.addChild(childNode);
            } else {
                // If child is not an API call, promote its children to this level
                for (TreeNode grandChild : childNode.getChildren()) {
                    node.addChild(grandChild);
                }
            }
        }
        
        // Remove duplicates from children
        deduplicateChildren(node);
        
        return node;
    }
    
    /**
     * Determines if a TreeNode represents an actual API call (not gateway or internal operation).
     * Actual API calls should have an HTTP method and a path starting with /api/v1/
     */
    private boolean isActualApiCall(TreeNode node) {
        String path = node.getApiPath();
        String httpMethod = node.getHttpMethod();
        
        // Must have a valid HTTP method (GET, POST, PUT, DELETE, PATCH)
        if (httpMethod == null || httpMethod.equals("UNKNOWN") || httpMethod.isEmpty()) {
            return false;
        }
        
        // Must have a real, rooted API path (any base path, not just /api/v1/).
        // Reject nulls, non-rooted paths, and bare wildcards like "/", "/*", "/**".
        if (path == null || !path.startsWith("/")) {
            return false;
        }
        String trimmedPath = path.trim();
        if (trimmedPath.equals("/") || trimmedPath.equals("/*") || trimmedPath.equals("/**")) {
            return false;
        }
        
        // Exclude gateway service calls (they just proxy to real services)
        if (node.getServiceName() != null && node.getServiceName().toLowerCase().contains("gateway")) {
            return false;
        }
        
        // Exclude any path that looks like an internal operation (contains dots or capitals indicating method names)
        if (path.contains(".") || path.matches(".*[A-Z].*")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Recursively removes duplicate API calls in the tree.
     * This handles cases where the same API appears multiple times due to gateway forwarding.
     */
    private void deduplicateChildren(TreeNode node) {
        if (node.getChildren().isEmpty()) {
            return;
        }
        
        // Use a Set to track unique API calls (method + path)
        java.util.Set<String> seenApis = new java.util.HashSet<>();
        java.util.List<TreeNode> uniqueChildren = new java.util.ArrayList<>();
        
        for (TreeNode child : node.getChildren()) {
            String apiKey = child.getHttpMethod() + " " + child.getApiPath();
            if (!seenApis.contains(apiKey)) {
                seenApis.add(apiKey);
                uniqueChildren.add(child);
                // Recursively deduplicate this child's children
                deduplicateChildren(child);
            }
        }
        
        // Replace children with deduplicated list
        node.replaceChildren(uniqueChildren);
    }
    
    /**
     * Extracts HTTP method from a workflow step.
     */
    private String extractHttpMethod(WorkflowStep step) {
        return step.getInputFields().getOrDefault("http.method", "UNKNOWN");
    }
    
    /**
     * Extracts API path from a workflow step.
     */
    private String extractApiPath(WorkflowStep step) {
        String target = step.getInputFields().get("http.target");
        if (target != null && !target.isEmpty()) {
            int queryIndex = target.indexOf('?');
            return queryIndex > 0 ? target.substring(0, queryIndex) : target;
        }
        
        String url = step.getInputFields().get("http.url");
        if (url != null && !url.isEmpty()) {
            try {
                java.net.URI parsedUri = java.net.URI.create(url);
                String path = parsedUri.getPath();
                return path != null && !path.isEmpty() ? path : "/";
            } catch (Exception e) {
                // Fallback to operation name
            }
        }
        
        return step.getOperationName();
    }
    
    /**
     * Converts this tree to JSON format.
     * Simplified to show only essential information.
     */
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("source_trace", sourceTrace);
        
        // Only include children, not the root itself (since root is already the API key)
        JSONArray childrenArray = new JSONArray();
        for (TreeNode child : root.getChildren()) {
            childrenArray.put(child.toJson());
        }
        json.put("children", childrenArray);
        
        return json;
    }
    
    /**
     * Creates an ApiTree from JSON data.
     * Creates a dummy root node from the children array if needed.
     */
    public static ApiTree fromJson(JSONObject json) {
        String treeId = json.optString("tree_id", "");
        String sourceTrace = json.optString("source_trace", "");
        
        // Create a dummy root node and populate it with children from JSON
        TreeNode root = new TreeNode("", "", "", "");
        
        if (json.has("children")) {
            JSONArray childrenArray = json.getJSONArray("children");
            for (int i = 0; i < childrenArray.length(); i++) {
                TreeNode child = TreeNode.fromJson(childrenArray.getJSONObject(i));
                if (child != null) {
                    root.addChild(child);
                }
            }
        } else if (json.has("root")) {
            // Legacy format support
            root = TreeNode.fromJson(json.optJSONObject("root"));
        }
        
        return new ApiTree(treeId, sourceTrace, root);
    }
    
    // Getters
    public String getTreeId() { return treeId; }
    public String getSourceTrace() { return sourceTrace; }
    public TreeNode getRoot() { return root; }
    
    @Override
    public String toString() {
        return String.format("ApiTree{id='%s', source='%s', root=%s}", 
                           treeId, sourceTrace, root.getServiceName() + ":" + root.getOperation());
    }
    
    /**
     * Simple tree node representing a microservice API call in the interaction tree.
     */
    public static class TreeNode {
        private final String serviceName;
        private final String operation;
        private final String httpMethod;
        private final String apiPath;
        private final java.util.List<TreeNode> children = new java.util.ArrayList<>();
        
        public TreeNode(String serviceName, String operation, String httpMethod, String apiPath) {
            this.serviceName = serviceName;
            this.operation = operation;
            this.httpMethod = httpMethod;
            this.apiPath = apiPath;
        }
        
        public void addChild(TreeNode child) {
            children.add(child);
        }
        
        public void replaceChildren(java.util.List<TreeNode> newChildren) {
            children.clear();
            children.addAll(newChildren);
        }
        
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            // Only output method and path for cleaner format
            json.put("method", httpMethod);
            json.put("path", apiPath);
            
            if (!children.isEmpty()) {
                JSONArray childrenArray = new JSONArray();
                for (TreeNode child : children) {
                    childrenArray.put(child.toJson());
                }
                json.put("children", childrenArray);
            }
            
            return json;
        }
        
        public static TreeNode fromJson(JSONObject json) {
            if (json == null) return null;
            
            // Support both old and new JSON formats
            String serviceName = json.optString("service", "unknown");
            String operation = json.optString("operation", "unknown");
            String httpMethod = json.optString("method", json.optString("http_method", "UNKNOWN"));
            String apiPath = json.optString("path", json.optString("api_path", ""));
            
            TreeNode node = new TreeNode(serviceName, operation, httpMethod, apiPath);
            
            if (json.has("children")) {
                JSONArray childrenArray = json.getJSONArray("children");
                for (int i = 0; i < childrenArray.length(); i++) {
                    TreeNode child = fromJson(childrenArray.getJSONObject(i));
                    if (child != null) {
                        node.addChild(child);
                    }
                }
            }
            
            return node;
        }
        
        // Getters
        public String getServiceName() { return serviceName; }
        public String getOperation() { return operation; }
        public String getHttpMethod() { return httpMethod; }
        public String getApiPath() { return apiPath; }
        public java.util.List<TreeNode> getChildren() { 
            return java.util.Collections.unmodifiableList(children); 
        }
        
        @Override
        public String toString() {
            return String.format("TreeNode{service='%s', operation='%s', children=%d}", 
                               serviceName, operation, children.size());
        }
    }
}
