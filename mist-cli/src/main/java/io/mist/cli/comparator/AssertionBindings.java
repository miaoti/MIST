package io.mist.cli.comparator;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict loader for the FROZEN executable bindings of the G2 comparator
 * (assertion-bindings-*.yaml): the mechanical per-clause translation of the
 * blind-authored prose contracts into the closed primitive set of
 * comparator-runner-design.md §2. Like {@link io.mist.cli.fault.TargetTripleRegistry},
 * the file is small and hand-frozen, so parsing fails loudly on anything
 * unknown rather than degrading silently — a binding error at run time must be
 * a comparator-infra-failure, never a quiet mis-evaluation.
 */
public final class AssertionBindings {

    /** Closed primitive set (design §2). */
    public enum Primitive { HTTP_STATUS, ENVELOPE_STATUS, ENVELOPE_DATA, MSG_CONTAINS, STATE_GET, NOT_CHECKABLE }

    /** One executable check bound to (part of) a frozen clause. */
    public static final class Check {
        public final Primitive primitive;
        /** HTTP_STATUS: comma-separated statuses; ENVELOPE_STATUS: int; ENVELOPE_DATA: null|non-null; MSG_CONTAINS: substring; STATE_GET: contains-submitted-fields|absent. */
        public final String expect;
        /** STATE_GET only: the read-back path. */
        public final String path;
        /** STATE_GET contains-submitted-fields only: body field names forming the membership key. */
        public final List<String> fields;
        /** NOT_CHECKABLE only: why the clause is not executable. */
        public final String reason;
        /**
         * STATE_GET contains-literal-fields only (P2): the body key holding the collection to
         * unwrap — e.g. {@code "health"} for a {@code {health:[..]}} body that the default
         * array / {@code {data:[]}} / {@code {_embedded}} shapes do not cover. Null (the default)
         * falls back to those shapes.
         */
        public final String collectionKey;

        Check(Primitive primitive, String expect, String path, List<String> fields, String reason,
              String collectionKey) {
            this.primitive = primitive;
            this.expect = expect;
            this.path = path;
            this.fields = fields == null ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(fields));
            this.reason = reason;
            this.collectionKey = collectionKey;
        }
    }

    /** One frozen clause (cited verbatim) with its bound checks. */
    public static final class Clause {
        public final String cite;
        public final List<Check> checks;

        Clause(String cite, List<Check> checks) {
            this.cite = cite;
            this.checks = Collections.unmodifiableList(new ArrayList<>(checks));
        }
    }

    /** One bound write endpoint. */
    public static final class BoundEndpoint {
        public final String endpoint;
        /** Name of the target-triple carrying the fault flag + hygiene coords. */
        public final String triple;
        /** JSON body with ${uuid:FIELD} / ${fresh:FIELD} substitution tokens. */
        public final String bodyTemplate;
        public final List<Clause> clauses;

        BoundEndpoint(String endpoint, String triple, String bodyTemplate, List<Clause> clauses) {
            this.endpoint = endpoint;
            this.triple = triple;
            this.bodyTemplate = bodyTemplate;
            this.clauses = Collections.unmodifiableList(new ArrayList<>(clauses));
        }
    }

    public static final class Bindings {
        public final String sut;
        public final String frozenSet;
        public final List<BoundEndpoint> endpoints;

        Bindings(String sut, String frozenSet, List<BoundEndpoint> endpoints) {
            this.sut = sut;
            this.frozenSet = frozenSet;
            this.endpoints = Collections.unmodifiableList(new ArrayList<>(endpoints));
        }
    }

    private static final Set<String> ALLOWED_TOP = set("sut", "frozen_set", "endpoints");
    private static final Set<String> ALLOWED_ENDPOINT = set("endpoint", "triple", "body_template", "clauses");
    private static final Set<String> ALLOWED_CLAUSE = set("cite", "checks");
    private static final Set<String> ALLOWED_CHECK =
            set("primitive", "expect", "path", "fields", "reason", "collection_key");

    private static Set<String> set(String... keys) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(keys)));
    }

    private AssertionBindings() {
        // static loader only
    }

    public static Bindings load(Path yamlFile) throws IOException {
        try (InputStream in = Files.newInputStream(yamlFile)) {
            return parse(in, yamlFile.toString());
        }
    }

    @SuppressWarnings("unchecked")
    public static Bindings parse(InputStream in, String origin) {
        Object raw;
        try {
            raw = new Yaml().load(in);
        } catch (RuntimeException re) {
            throw new IllegalArgumentException(
                    "AssertionBindings: malformed YAML in " + origin + " — " + re.getMessage(), re);
        }
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException("AssertionBindings: " + origin + " must be a map");
        }
        Map<String, Object> root = (Map<String, Object>) raw;
        rejectUnknown(root.keySet(), ALLOWED_TOP, "top-level", origin);
        String sut = requireString(root, "sut", origin);
        String frozenSet = requireString(root, "frozen_set", origin);
        Object endpointsNode = root.get("endpoints");
        if (!(endpointsNode instanceof List) || ((List<?>) endpointsNode).isEmpty()) {
            throw new IllegalArgumentException("AssertionBindings: " + origin
                    + " needs a non-empty 'endpoints' list");
        }
        List<BoundEndpoint> endpoints = new ArrayList<>();
        for (Object epNode : (List<Object>) endpointsNode) {
            Map<String, Object> ep = asMap(epNode, "endpoint entry", origin);
            rejectUnknown(ep.keySet(), ALLOWED_ENDPOINT, "endpoint", origin);
            List<Clause> clauses = new ArrayList<>();
            Object clausesNode = ep.get("clauses");
            if (!(clausesNode instanceof List) || ((List<?>) clausesNode).isEmpty()) {
                throw new IllegalArgumentException("AssertionBindings: endpoint in " + origin
                        + " needs a non-empty 'clauses' list");
            }
            for (Object clNode : (List<Object>) clausesNode) {
                Map<String, Object> cl = asMap(clNode, "clause", origin);
                rejectUnknown(cl.keySet(), ALLOWED_CLAUSE, "clause", origin);
                List<Check> checks = new ArrayList<>();
                Object checksNode = cl.get("checks");
                if (!(checksNode instanceof List) || ((List<?>) checksNode).isEmpty()) {
                    throw new IllegalArgumentException("AssertionBindings: clause in " + origin
                            + " needs a non-empty 'checks' list");
                }
                for (Object chNode : (List<Object>) checksNode) {
                    checks.add(parseCheck(asMap(chNode, "check", origin), origin));
                }
                clauses.add(new Clause(requireString(cl, "cite", origin), checks));
            }
            endpoints.add(new BoundEndpoint(
                    requireString(ep, "endpoint", origin),
                    requireString(ep, "triple", origin),
                    requireString(ep, "body_template", origin),
                    clauses));
        }
        return new Bindings(sut, frozenSet, endpoints);
    }

    private static Check parseCheck(Map<String, Object> ch, String origin) {
        rejectUnknown(ch.keySet(), ALLOWED_CHECK, "check", origin);
        String primitiveRaw = requireString(ch, "primitive", origin);
        Primitive primitive;
        try {
            primitive = Primitive.valueOf(primitiveRaw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("AssertionBindings: unknown primitive '" + primitiveRaw
                    + "' in " + origin + " (allowed: " + Arrays.toString(Primitive.values()) + ")");
        }
        String expect = optionalString(ch, "expect");
        String path = optionalString(ch, "path");
        String fieldsRaw = optionalString(ch, "fields");
        String reason = optionalString(ch, "reason");
        String collectionKey = optionalString(ch, "collection_key");
        switch (primitive) {
            case HTTP_STATUS:
                requireExpect(expect, primitive, origin);
                for (String s : expect.split(",")) {
                    try {
                        Integer.parseInt(s.trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("AssertionBindings: HTTP_STATUS expect must be "
                                + "comma-separated integers in " + origin + " (got '" + expect + "')");
                    }
                }
                break;
            case ENVELOPE_STATUS:
                requireExpect(expect, primitive, origin);
                try {
                    Integer.parseInt(expect.trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("AssertionBindings: ENVELOPE_STATUS expect must be "
                            + "an integer in " + origin);
                }
                break;
            case ENVELOPE_DATA:
                requireExpect(expect, primitive, origin);
                if (!"null".equals(expect) && !"non-null".equals(expect)) {
                    throw new IllegalArgumentException("AssertionBindings: ENVELOPE_DATA expect must be "
                            + "'null' or 'non-null' in " + origin);
                }
                break;
            case MSG_CONTAINS:
                requireExpect(expect, primitive, origin);
                break;
            case STATE_GET:
                requireExpect(expect, primitive, origin);
                if (path == null) {
                    throw new IllegalArgumentException("AssertionBindings: STATE_GET needs 'path' in " + origin);
                }
                if ("contains-submitted-fields".equals(expect)
                        || "entity-matches-submitted-fields".equals(expect)) {
                    if (fieldsRaw == null || fieldsRaw.trim().isEmpty()) {
                        throw new IllegalArgumentException("AssertionBindings: STATE_GET "
                                + expect + " needs 'fields' in " + origin);
                    }
                } else if ("contains-literal-fields".equals(expect)) {
                    // P2: literal name=value constraints (not submitted-body values), so a
                    // liveness clause like service=shipping-rabbitmq,status=OK is expressible.
                    if (fieldsRaw == null || fieldsRaw.trim().isEmpty()) {
                        throw new IllegalArgumentException("AssertionBindings: STATE_GET "
                                + "contains-literal-fields needs 'fields' (name=value constraints) in "
                                + origin);
                    }
                    for (String f : fieldsRaw.split(",")) {
                        if (!f.trim().isEmpty() && f.indexOf('=') <= 0) {
                            throw new IllegalArgumentException("AssertionBindings: STATE_GET "
                                    + "contains-literal-fields field '" + f.trim()
                                    + "' must be 'name=value' in " + origin);
                        }
                    }
                } else if (!"absent".equals(expect)) {
                    throw new IllegalArgumentException("AssertionBindings: STATE_GET expect must be "
                            + "'contains-submitted-fields', 'entity-matches-submitted-fields', "
                            + "'contains-literal-fields' or 'absent' in " + origin);
                }
                break;
            case NOT_CHECKABLE:
                if (reason == null || reason.trim().isEmpty()) {
                    throw new IllegalArgumentException("AssertionBindings: NOT_CHECKABLE needs a 'reason' in "
                            + origin);
                }
                break;
            default:
                throw new IllegalStateException("unhandled primitive " + primitive);
        }
        List<String> fields = new ArrayList<>();
        if (fieldsRaw != null) {
            for (String f : fieldsRaw.split(",")) {
                if (!f.trim().isEmpty()) {
                    fields.add(f.trim());
                }
            }
        }
        return new Check(primitive, expect, path, fields, reason, collectionKey);
    }

    private static void requireExpect(String expect, Primitive primitive, String origin) {
        if (expect == null || expect.trim().isEmpty()) {
            throw new IllegalArgumentException("AssertionBindings: " + primitive + " needs a non-empty "
                    + "'expect' in " + origin);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object node, String what, String origin) {
        if (!(node instanceof Map)) {
            throw new IllegalArgumentException("AssertionBindings: non-map " + what + " in " + origin);
        }
        return (Map<String, Object>) node;
    }

    private static void rejectUnknown(Set<String> keys, Set<String> allowed, String where, String origin) {
        for (String key : keys) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("AssertionBindings: unknown " + where + " key '" + key
                        + "' in " + origin + " (typo? allowed: " + allowed + ")");
            }
        }
    }

    private static String requireString(Map<String, Object> map, String key, String origin) {
        Object value = map.get(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new IllegalArgumentException("AssertionBindings: " + origin
                    + " needs a non-empty string '" + key + "'");
        }
        return ((String) value).trim();
    }

    private static String optionalString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return String.valueOf(value).trim();
    }
}
