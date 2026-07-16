package io.mist.cli.enable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mist.cli.fault.DataIntegrityRuntime;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Chained durable-store read-back for TeaStore's CHILD-COLLECTION (order-items) masked write —
 * completion-set wave, Phase C item A5(iii); the same sanctioned read-back-modality-binding
 * category as {@link JsonDurableReadback} (wave 2.75-A).
 *
 * <p>The order-items mesh-sever fault (r1-a2) loses the CHILD rows while the PARENT order row
 * lands, and the marker lives in the parent's {@code address1} — child rows never carry it. A
 * flat collection read therefore cannot express "the acked order's items are missing" as
 * MEMBERSHIP. This read-back builds the chain mechanically: GET {@code /rest/orders}, then for
 * each order GET {@code /rest/orderitems/order/{id}}, and return a SYNTHETIC JSON array
 * containing only the orders whose child collection is NON-EMPTY (each row keeps {@code id} +
 * {@code address1}). The reviewed MEMBERSHIP oracle's marker check over that body then reads:
 * present &hArr; the marker's order exists AND has &ge;1 item — absent on the fault leg (parent
 * landed, 0 items), present on control. No marker knowledge lives here (request-derived only,
 * review A-F5 discipline preserved).
 *
 * <p>Transport failures pass through as their status (or 0) — never mistaken for absence: a
 * non-2xx on the ORDERS read returns that status; a non-2xx on any ITEMS read fails loud with
 * status 0 and a diagnostic body (the paired-control discipline surfaces it as NOT_EVALUABLE
 * rather than a fake FIRE).
 */
public final class ChainedOrderItemsReadback implements DataIntegrityRuntime.Http {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String base;       // persistence base, e.g. http://localhost:8083/tools.descartes.teastore.persistence
    private final String scopeQuery; // optional ?userid=<n> bound on the ORDERS read; "" for none
    private final int timeoutMs;

    public ChainedOrderItemsReadback(String base, String scopeQuery, int timeoutMs) {
        if (base == null || base.trim().isEmpty()) {
            throw new IllegalArgumentException("ChainedOrderItemsReadback needs a persistence base URL");
        }
        this.base = base.trim().replaceAll("/+$", "");
        this.scopeQuery = scopeQuery == null ? "" : scopeQuery.trim();
        this.timeoutMs = timeoutMs;
    }

    @Override
    public DataIntegrityRuntime.HttpResponse getSut(String path) {
        DataIntegrityRuntime.HttpResponse orders = get(base + "/rest/orders" + scopeQuery);
        if (orders.status < 200 || orders.status >= 300) {
            return orders; // pass the transport truth through
        }
        try {
            JsonNode arr = JSON.readTree(orders.body);
            StringBuilder out = new StringBuilder("[");
            boolean first = true;
            for (JsonNode order : arr) {
                long id = order.path("id").asLong(-1);
                if (id < 0) {
                    continue;
                }
                DataIntegrityRuntime.HttpResponse items =
                        get(base + "/rest/orderitems/order/" + id);
                if (items.status < 200 || items.status >= 300) {
                    return new DataIntegrityRuntime.HttpResponse(0,
                            "{\"error\":\"orderitems read for id " + id + " -> HTTP "
                            + items.status + "\"}");
                }
                JsonNode itemArr = JSON.readTree(items.body);
                if (itemArr.isArray() && itemArr.size() > 0) {
                    if (!first) {
                        out.append(',');
                    }
                    out.append("{\"id\":").append(id).append(",\"address1\":")
                       .append(JSON.writeValueAsString(order.path("address1").asText("")))
                       .append(",\"items\":").append(itemArr.size()).append('}');
                    first = false;
                }
            }
            out.append(']');
            return new DataIntegrityRuntime.HttpResponse(200, out.toString());
        } catch (IOException e) {
            return new DataIntegrityRuntime.HttpResponse(0,
                    "{\"error\":\"chained readback parse: " + e.getMessage() + "\"}");
        }
    }

    @Override
    public DataIntegrityRuntime.HttpResponse getAbsolute(String url) {
        return get(url);
    }

    private DataIntegrityRuntime.HttpResponse get(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("Accept", "application/json");
            int status = c.getResponseCode();
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new DataIntegrityRuntime.HttpResponse(status, body);
        } catch (IOException e) {
            return new DataIntegrityRuntime.HttpResponse(0, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
