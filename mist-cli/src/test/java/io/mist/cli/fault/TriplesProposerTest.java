package io.mist.cli.fault;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * UX W3 (REVIEW-UX-RECONCILIATION U8): pins the proposal heuristics —
 * collection-shaped read-backs only, expert-tier writes never proposed,
 * required-string isolation keys, and a round-trip guarantee that the emitted
 * YAML (TODO filled) parses under the STRICT registry loader.
 */
public class TriplesProposerTest {

    private static OpenAPI api(PathItem... items) {
        OpenAPI api = new OpenAPI();
        Paths paths = new Paths();
        int i = 0;
        for (PathItem item : items) {
            paths.addPathItem(item.get$ref() != null ? item.get$ref() : "/p" + (i++), item);
        }
        api.setPaths(paths);
        return api;
    }

    private static OpenAPI apiWith(String path, PathItem item) {
        OpenAPI api = new OpenAPI();
        Paths paths = new Paths();
        paths.addPathItem(path, item);
        api.setPaths(paths);
        return api;
    }

    private static Operation postWithBody(Schema<?> reqSchema) {
        Operation post = new Operation();
        post.setRequestBody(new RequestBody().content(new Content()
                .addMediaType("application/json", new MediaType().schema(reqSchema))));
        ApiResponses rs = new ApiResponses();
        rs.addApiResponse("200", new ApiResponse().description("ok"));
        post.setResponses(rs);
        return post;
    }

    private static Operation getReturning(Schema<?> respSchema) {
        Operation get = new Operation();
        ApiResponses rs = new ApiResponses();
        rs.addApiResponse("200", new ApiResponse().description("ok").content(new Content()
                .addMediaType("application/json", new MediaType().schema(respSchema))));
        get.setResponses(rs);
        return get;
    }

    private static Schema<?> contactsRequest() {
        ObjectSchema req = new ObjectSchema();
        req.addProperties("accountId", new StringSchema());
        req.addProperties("documentNumber", new StringSchema());
        req.addProperties("id", new StringSchema());        // server-generated → excluded
        req.setRequired(Arrays.asList("accountId", "documentNumber"));
        return req;
    }

    private static Schema<?> dataEnvelope() {
        ObjectSchema resp = new ObjectSchema();
        resp.addProperties("status", new Schema<Integer>().type("integer"));
        resp.addProperties("msg", new StringSchema());
        resp.addProperties("data", new ArraySchema().items(new ObjectSchema()));
        return resp;
    }

    @Test
    public void dataEnvelopeGet_isProposedHigh_withRequiredStringKeys() {
        PathItem item = new PathItem().post(postWithBody(contactsRequest()))
                .get(getReturning(dataEnvelope()));
        List<TriplesProposer.Proposal> ps = TriplesProposer.propose(
                apiWith("/api/v1/adminbasicservice/adminbasic/contacts", item));
        assertEquals(1, ps.size());
        TriplesProposer.Proposal p = ps.get(0);
        assertEquals("POST /api/v1/adminbasicservice/adminbasic/contacts", p.writeEndpoint);
        assertEquals("GET /api/v1/adminbasicservice/adminbasic/contacts", p.readbackEndpoint);
        assertEquals("high", p.confidence);
        assertEquals(Arrays.asList("accountId", "documentNumber"), p.isolationKey);
    }

    @Test
    public void bareArrayGet_isProposedHigh() {
        PathItem item = new PathItem().post(postWithBody(contactsRequest()))
                .get(getReturning(new ArraySchema().items(new ObjectSchema())));
        List<TriplesProposer.Proposal> ps = TriplesProposer.propose(apiWith("/things", item));
        assertEquals(1, ps.size());
        assertEquals("high", ps.get(0).confidence);
    }

    @Test
    public void singleObjectGet_isNeverProposed() {
        // U8: a per-entity/object read-back is not collection-shaped — the
        // runtime cannot evaluate it → proposing it would mean perpetual
        // false LOST. Must be skipped.
        ObjectSchema single = new ObjectSchema();
        single.addProperties("accountId", new StringSchema());
        PathItem item = new PathItem().post(postWithBody(contactsRequest()))
                .get(getReturning(single));
        assertTrue(TriplesProposer.propose(apiWith("/contacts", item)).isEmpty());
    }

    @Test
    public void bodylessWrite_isNeverProposed_expertTier() {
        Operation post = new Operation();
        ApiResponses rs = new ApiResponses();
        rs.addApiResponse("200", new ApiResponse().description("ok"));
        post.setResponses(rs);
        PathItem item = new PathItem().post(post).get(getReturning(dataEnvelope()));
        assertTrue("bodyless writes are the expert tier — never proposed",
                TriplesProposer.propose(apiWith("/cancel/{orderId}", item)).isEmpty());
    }

    @Test
    public void postWithoutSamePathGet_isNotProposed() {
        PathItem item = new PathItem().post(postWithBody(contactsRequest()));
        assertTrue(TriplesProposer.propose(apiWith("/orders", item)).isEmpty());
    }

    @Test
    public void emittedYaml_isReviewGated_andParsesUnderStrictLoaderOnceTodoFilled() {
        PathItem item = new PathItem().post(postWithBody(contactsRequest()))
                .get(getReturning(dataEnvelope()));
        List<TriplesProposer.Proposal> ps = TriplesProposer.propose(
                apiWith("/api/v1/adminbasicservice/adminbasic/contacts", item));
        String yaml = TriplesProposer.toYaml(ps);
        assertTrue("must carry the review gate", yaml.contains("REVIEW BEFORE USE"));
        assertTrue("must carry the dependency TODO", yaml.contains("TODO-REVIEW"));
        assertTrue("proposals use the safe default strategy", yaml.contains("isolation_strategy: fresh-strings"));

        // Round-trip: once the human fills the TODO, the STRICT loader accepts it.
        String filled = yaml.replace("TODO-REVIEW  # TODO(dependency): trace-matchable name of the PERSISTING service",
                "ts-contacts-service");
        TargetTripleRegistry.Registry reg = TargetTripleRegistry.parse(
                new ByteArrayInputStream(filled.getBytes(StandardCharsets.UTF_8)), "proposed");
        assertEquals(1, reg.triples.size());
        assertEquals("POST /api/v1/adminbasicservice/adminbasic/contacts",
                reg.triples.get(0).writeEndpoint);
    }
}
