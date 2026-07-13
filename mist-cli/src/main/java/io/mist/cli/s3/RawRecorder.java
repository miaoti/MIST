package io.mist.cli.s3;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Journey-side raw transcript recorder for the S3 wild-hunt (plan rev 2.1 §4.3). Our-side format
 * with ABSOLUTE times (the Python assembler rebases to per-case relative offsets — review B-M6a).
 * REDACTION IS THE PRODUCER'S JOB (format rule 5, review B-M6d): credential-bearing query strings
 * and bodies are redacted AT RECORD TIME via {@link #redactPath}/{@link #redactBody}; probe
 * descriptors must stay human-neutral (never Java class names — review A-m3).
 */
public final class RawRecorder {

    /** One raw record (request or response), absolute-time-stamped our-side. */
    public static final class Rec {
        public final long tAbsMs;
        public final String kind;    // "request" | "response"
        public final String method;  // request only
        public final String path;    // request only (redacted)
        public final String payload; // request only (redacted; null when none)
        public final Integer status; // response only
        public final String body;    // response only (redacted)

        Rec(long tAbsMs, String kind, String method, String path, String payload,
            Integer status, String body) {
            this.tAbsMs = tAbsMs;
            this.kind = kind;
            this.method = method;
            this.path = path;
            this.payload = payload;
            this.status = status;
            this.body = body;
        }

        JSONObject toJson() {
            JSONObject j = new JSONObject();
            j.put("t_abs_ms", tAbsMs).put("kind", kind);
            if ("request".equals(kind)) {
                j.put("method", method).put("path", path);
                if (payload != null) {
                    j.put("payload", payload);
                }
            } else {
                j.put("status", status).put("body", body == null ? "" : body);
            }
            return j;
        }
    }

    /** Credential fields redacted at record time (TT register/login; TeaStore loginAction). */
    private static final Pattern CRED_QUERY = Pattern.compile(
            "(username|password|userName)=[^&]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern CRED_JSON = Pattern.compile(
            "\"(password|userName|username)\"\\s*:\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE);

    private final List<Rec> records = new ArrayList<>();

    public static String redactPath(String path) {
        if (path == null) {
            return null;
        }
        if (path.contains("loginAction") && path.contains("=")) {
            int q = path.indexOf('?');
            return (q < 0 ? path : path.substring(0, q)) + "?<credentials redacted>";
        }
        return CRED_QUERY.matcher(path).replaceAll("$1=<redacted>");
    }

    public static String redactBody(String body) {
        if (body == null) {
            return null;
        }
        return CRED_JSON.matcher(body).replaceAll("\"$1\":\"<redacted>\"");
    }

    public void request(String method, String path, String payload) {
        records.add(new Rec(System.currentTimeMillis(), "request", method, redactPath(path),
                redactBody(payload), null, null));
    }

    public void response(int status, String body) {
        records.add(new Rec(System.currentTimeMillis(), "response", null, null, null, status,
                redactBody(body)));
    }

    public List<Rec> records() {
        return Collections.unmodifiableList(records);
    }
}
