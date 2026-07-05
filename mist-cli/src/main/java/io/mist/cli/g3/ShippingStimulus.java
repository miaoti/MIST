package io.mist.cli.g3;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.json.JSONObject;

import java.util.UUID;

/**
 * The live shipping-service {@link ShippingEnqueueHeadToHead.Stimulus}: POST /shipping with a
 * fresh {@code {id,name}}. This is the one SUT-specific boundary; everything else in the
 * head-to-head is SUT-agnostic and unit-tested. The POST targets {@code RestAssured.baseURI}
 * (set to {@code -Dg3.ship.base} by {@link ShippingEnqueueHeadToHead#run}).
 *
 * <p>{@code main()} is the live launcher. Required -D properties (all live/SUT-specific):
 * {@code g3.ship.base} (shipping gateway, e.g. a port-forward of shipping:80),
 * {@code g3.ship.rmq.base}/{@code .rmq.user}/{@code .rmq.pass} (broker mgmt read-back — a
 * port-forward of the rabbitmq pod's 15672, mist:mist), {@code g3.ship.triple} (the value-delta
 * triple), {@code g3.ship.contract.path} (the frozen blind contract), {@code g3.ship.sever.manifest}
 * (the natural-stratum Istio sever); optional {@code g3.ship.strata} (default "natural,constructed"),
 * {@code g3.ship.kubectl}/{@code .kubeconfig}/{@code .namespace}/{@code .converge.seconds}.
 */
public final class ShippingStimulus implements ShippingEnqueueHeadToHead.Stimulus {

    @Override
    public ShippingEnqueueHeadToHead.Shipment postShipping() {
        String id = UUID.randomUUID().toString();
        String name = "n-" + id;
        Response r = RestAssured.given()
                .contentType("application/json")
                .body(new JSONObject().put("id", id).put("name", name).toString())
                .post("/shipping");
        return new ShippingEnqueueHeadToHead.Shipment(id, name, r.statusCode(), r.body().asString());
    }

    public static void main(String[] args) throws Exception {
        ShippingEnqueueHeadToHead.run(new ShippingStimulus());
    }
}
