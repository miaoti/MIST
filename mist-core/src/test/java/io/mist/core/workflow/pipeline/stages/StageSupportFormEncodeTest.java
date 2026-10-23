package io.mist.core.workflow.pipeline.stages;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Tests {@link StageSupport#formUrlEncode}: the body of an HTML-form operation keeps the field order,
 * URL-encodes names and values, sends an empty value as {@code name=} and leaves a null field out, so
 * an empty-input and a null-input negative test send different requests.
 */
public class StageSupportFormEncodeTest {

    @Test
    public void fieldsInOrder() {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("product_id", "OLJCESPC7Z");
        f.put("quantity", 2);
        assertEquals("product_id=OLJCESPC7Z&quantity=2", StageSupport.formUrlEncode(f));
    }

    @Test
    public void specialCharactersAreEncoded() {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("email", "a b&c=d@x.io");
        assertEquals("email=a+b%26c%3Dd%40x.io", StageSupport.formUrlEncode(f));
    }

    @Test
    public void emptyValueIsSentAndNullFieldIsLeftOut() {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("currency_code", "");
        assertEquals("currency_code=", StageSupport.formUrlEncode(empty));

        Map<String, Object> nulls = new LinkedHashMap<>();
        nulls.put("product_id", null);
        nulls.put("quantity", 2);
        assertEquals("quantity=2", StageSupport.formUrlEncode(nulls));
    }
}
