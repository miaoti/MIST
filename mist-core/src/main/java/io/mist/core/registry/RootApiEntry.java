package io.mist.core.registry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONObject;
import io.mist.core.workflow.WorkflowStep;

/**
 * Represents a single Root API endpoint entry in the registry.
 * Each entry contains the API metadata (method, path, service) and multiple
 * tree structures representing different execution patterns observed from traces.
 * 
 * This class is thread-safe and supports multiple tree patterns for the same root API,
 * which is common in microservice systems where the same endpoint can trigger
 * different service interaction patterns based on input parameters or system state.
 */
public class RootApiEntry {
    private final String method;
    private final String path;
    private final String serviceName;
    private final String operationName;
    private final Map<String, ApiTree> trees = new ConcurrentHashMap<>();
    
    /**
     * Creates a new RootApiEntry from a WorkflowStep representing the root API.
     */
    public RootApiEntry(WorkflowStep rootStep) {
        this.method = rootStep.getInputFields().getOrDefault("http.method", "UNKNOWN");
        this.path = extractApiPath(rootStep);
        this.serviceName = rootStep.getServiceName();
        this.operationName = rootStep.getOperationName();
    }
    
    /**
     * Private constructor for deserialization from JSON.
     */
    private RootApiEntry(String method, String path, String serviceName, String operationName) {
        this.method = method;
        this.path = path;
        this.serviceName = serviceName;
        this.operationName = operationName;
    }
    
    /**
     * Adds a new tree structure for this root API if it doesn't already exist.
     * Compares trees based on their actual API call children (method + path).
     * If the new tree is a subset of an existing tree, it's skipped.
     * If an existing tree is a subset of the new tree, it's replaced with the new (more complete) tree.
     * 
     * @param treeId unique identifier for this tree pattern
     * @param sourceTrace name of the trace file this tree came from
     * @param rootStep the root step with its complete tree structure
     * @return true if a new tree was added or replaced an existing one, false if skipped as duplicate/subset
     */
    public boolean addTree(String treeId, String sourceTrace, WorkflowStep rootStep) {
        // Create new tree candidate
        ApiTree newTree = new ApiTree(treeId, sourceTrace, rootStep, "");
        
        // Check against all existing trees
        String treeToReplace = null;
        for (Map.Entry<String, ApiTree> entry : trees.entrySet()) {
            ApiTree existingTree = entry.getValue();
            
            // Check if trees are identical
            if (treesHaveSameChildren(existingTree, newTree)) {
                return false; // Exact duplicate - skip
            }
            
            // Check if new tree is a subset of existing tree
            if (isSubsetOf(newTree, existingTree)) {
                // New tree is subset of existing - skip adding new
                return false;
            }
            
            // Check if existing tree is a subset of new tree
            if (isSubsetOf(existingTree, newTree)) {
                // Existing tree is subset of new - mark for replacement
                treeToReplace = entry.getKey();
                break; // Found one to replace, that's enough
            }
        }
        
        // If we found a subset tree to replace, remove it first
        if (treeToReplace != null) {
            trees.remove(treeToReplace);
        }
        
        // Add new tree
        trees.put(treeId, newTree);
        return true;
    }
    
    /**
     * Compares two ApiTrees to see if they have the same children structure.
     * Only compares the children lists, not the root nodes themselves.
     */
    private boolean treesHaveSameChildren(ApiTree tree1, ApiTree tree2) {
        List<ApiTree.TreeNode> children1 = tree1.getRoot().getChildren();
        List<ApiTree.TreeNode> children2 = tree2.getRoot().getChildren();
        
        // Compare number of children
        if (children1.size() != children2.size()) {
            return false;
        }
        
        // Sort children by method+path for consistent comparison
        List<ApiTree.TreeNode> sortedChildren1 = new ArrayList<>(children1);
        List<ApiTree.TreeNode> sortedChildren2 = new ArrayList<>(children2);
        
        java.util.Comparator<ApiTree.TreeNode> comparator = getNodeComparator();
        
        sortedChildren1.sort(comparator);
        sortedChildren2.sort(comparator);
        
        // Recursively compare each child tree
        for (int i = 0; i < sortedChildren1.size(); i++) {
            if (!compareTreeNodes(sortedChildren1.get(i), sortedChildren2.get(i))) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Checks if tree1 is a subset of tree2.
     * tree1 is a subset if all its paths exist in tree2, but tree2 may have additional paths.
     * Example: 1->2 is a subset of 1->2->3
     */
    private boolean isSubsetOf(ApiTree tree1, ApiTree tree2) {
        return isNodeSubsetOf(tree1.getRoot(), tree2.getRoot());
    }
    
    /**
     * Recursively checks if node1 is a subset of node2.
     * node1 is a subset if:
     * - It has the same API call (method + path)
     * - All its children exist as children in node2 (but node2 may have more children)
     * - Each child of node1 is also a subset of the corresponding child in node2
     */
    private boolean isNodeSubsetOf(ApiTree.TreeNode node1, ApiTree.TreeNode node2) {
        // Compare API calls (for non-root nodes)
        if (!node1.getHttpMethod().isEmpty() && !node2.getHttpMethod().isEmpty()) {
            if (!node1.getHttpMethod().equals(node2.getHttpMethod())) {
                return false;
            }
            if (!node1.getApiPath().equals(node2.getApiPath())) {
                return false;
            }
        }
        
        // If node1 has no children, it's a valid subset (shorter path)
        if (node1.getChildren().isEmpty()) {
            return true;
        }
        
        // If node1 has children but node2 doesn't, node1 cannot be a subset
        if (node2.getChildren().isEmpty()) {
            return false;
        }
        
        // Sort both children lists for comparison
        List<ApiTree.TreeNode> children1 = new ArrayList<>(node1.getChildren());
        List<ApiTree.TreeNode> children2 = new ArrayList<>(node2.getChildren());
        
        java.util.Comparator<ApiTree.TreeNode> comparator = getNodeComparator();
        children1.sort(comparator);
        children2.sort(comparator);
        
        // Each child of node1 must match a child in node2 and be a subset of it
        for (ApiTree.TreeNode child1 : children1) {
            boolean foundMatch = false;
            for (ApiTree.TreeNode child2 : children2) {
                // Check if child1 matches child2's API call
                if (child1.getHttpMethod().equals(child2.getHttpMethod()) &&
                    child1.getApiPath().equals(child2.getApiPath())) {
                    // Check if child1 is a subset of child2
                    if (isNodeSubsetOf(child1, child2)) {
                        foundMatch = true;
                        break;
                    }
                }
            }
            if (!foundMatch) {
                return false; // child1 has no matching subset in node2
            }
        }
        
        return true;
    }
    
    /**
     * Returns a comparator for sorting TreeNodes by method and path.
     */
    private java.util.Comparator<ApiTree.TreeNode> getNodeComparator() {
        return (a, b) -> {
            int methodCompare = a.getHttpMethod().compareTo(b.getHttpMethod());
            if (methodCompare != 0) return methodCompare;
            return a.getApiPath().compareTo(b.getApiPath());
        };
    }
    
    /**
     * Recursively compares two TreeNodes to check if they have the same structure.
     */
    private boolean compareTreeNodes(ApiTree.TreeNode node1, ApiTree.TreeNode node2) {
        // Compare the API calls (method + path)
        if (!node1.getHttpMethod().equals(node2.getHttpMethod())) {
            return false;
        }
        if (!node1.getApiPath().equals(node2.getApiPath())) {
            return false;
        }
        
        // Compare number of children
        if (node1.getChildren().size() != node2.getChildren().size()) {
            return false;
        }
        
        // Sort children by method+path for consistent comparison
        List<ApiTree.TreeNode> children1 = new ArrayList<>(node1.getChildren());
        List<ApiTree.TreeNode> children2 = new ArrayList<>(node2.getChildren());
        
        java.util.Comparator<ApiTree.TreeNode> comparator = (a, b) -> {
            int methodCompare = a.getHttpMethod().compareTo(b.getHttpMethod());
            if (methodCompare != 0) return methodCompare;
            return a.getApiPath().compareTo(b.getApiPath());
        };
        
        children1.sort(comparator);
        children2.sort(comparator);
        
        // Recursively compare each child
        for (int i = 0; i < children1.size(); i++) {
            if (!compareTreeNodes(children1.get(i), children2.get(i))) {
                return false;
            }
        }
        
        return true;
    }
    
    
    /**
     * Extracts the API path from the root step.
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
                // Fallback to operation name if URL parsing fails
            }
        }
        
        return step.getOperationName();
    }
    
    /**
     * Converts this entry to JSON format for storage.
     * Simplified format with only method and path at root level.
     */
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("method", method);
        json.put("path", path);
        
        JSONArray treesArray = new JSONArray();
        for (ApiTree tree : trees.values()) {
            treesArray.put(tree.toJson());
        }
        json.put("trees", treesArray);
        
        return json;
    }
    
    /**
     * Creates a RootApiEntry from JSON data.
     */
    public static RootApiEntry fromJson(JSONObject json) {
        String method = json.optString("method", "UNKNOWN");
        String path = json.optString("path", "");
        String serviceName = json.optString("service", "unknown");
        String operationName = json.optString("operation", "unknown");
        
        RootApiEntry entry = new RootApiEntry(method, path, serviceName, operationName);
        
        if (json.has("trees")) {
            JSONArray treesArray = json.getJSONArray("trees");
            for (int i = 0; i < treesArray.length(); i++) {
                JSONObject treeJson = treesArray.getJSONObject(i);
                ApiTree tree = ApiTree.fromJson(treeJson);
                entry.trees.put(tree.getTreeId(), tree);
            }
        }
        
        return entry;
    }
    
    // Getters
    public String getMethod() { return method; }
    public String getPath() { return path; }
    public String getServiceName() { return serviceName; }
    public String getOperationName() { return operationName; }
    public Collection<ApiTree> getTrees() { return Collections.unmodifiableCollection(trees.values()); }
    public ApiTree getTree(String treeId) { return trees.get(treeId); }
    
    @Override
    public String toString() {
        return String.format("RootApiEntry{method='%s', path='%s', service='%s', trees=%d}", 
                           method, path, serviceName, trees.size());
    }
}
