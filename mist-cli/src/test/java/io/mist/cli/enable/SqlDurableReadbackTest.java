package io.mist.cli.enable;

import io.mist.cli.fault.DataIntegrityRuntime;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link SqlDurableReadback}'s transport contract with a fake {@link
 * SqlDurableReadback.CommandRunner} (no live cluster): (1) a present marker synthesizes the
 * one-item MEMBERSHIP collection the oracle parses; (2) no matching row synthesizes {@code []}
 * (absence); (3) a non-zero exit / thrown exec is a non-2xx UNUSABLE read, NEVER {@code []}
 * (the error-vs-absence rule, review A-F5/B-F8); (4) a marker carrying a quote is rejected before
 * any command runs (injection defence, review B-F8); (5) the marker reaches the SQL server-side
 * (review B-R1).
 */
public class SqlDurableReadbackTest {

    private static final String QUERY =
            "SELECT street_address FROM accounting.shipping WHERE street_address='%s'";
    private static final List<String> PREFIX = List.of(
            "kubectl", "-n", "otel-demo", "exec", "-i", "pg-0", "--",
            "env", "PGPASSWORD=otel", "psql", "-U", "root", "-d", "otel", "-t", "-A", "-c");

    private SqlDurableReadback readback(AtomicReference<String> marker,
                                        SqlDurableReadback.CommandRunner runner) {
        return new SqlDurableReadback(PREFIX, "street_address", QUERY, marker::get, 5000, runner);
    }

    @Test
    public void presentMarker_synthesizesOneItemCollection_andFiltersServerSide() {
        AtomicReference<String> marker = new AtomicReference<>("mist-run7-p3");
        AtomicReference<List<String>> seenArgv = new AtomicReference<>();
        SqlDurableReadback.CommandRunner runner = (argv, t) -> {
            seenArgv.set(argv);
            return new SqlDurableReadback.CommandRunner.Result(0, "mist-run7-p3\n", "");
        };
        DataIntegrityRuntime.HttpResponse r = readback(marker, runner).getSut("/ignored");
        assertEquals(200, r.status);
        assertEquals("[{\"street_address\":\"mist-run7-p3\"}]", r.body);
        // B-R1: the unique marker is filtered SERVER-SIDE (in the SQL), not client-side.
        String sql = seenArgv.get().get(seenArgv.get().size() - 1);
        assertTrue("marker must reach the WHERE clause", sql.contains("street_address='mist-run7-p3'"));
    }

    @Test
    public void noMatchingRow_synthesizesEmptyCollection_absence() {
        AtomicReference<String> marker = new AtomicReference<>("mist-run7-p4");
        SqlDurableReadback.CommandRunner runner =
                (argv, t) -> new SqlDurableReadback.CommandRunner.Result(0, "\n", "");
        DataIntegrityRuntime.HttpResponse r = readback(marker, runner).getSut("/ignored");
        assertEquals(200, r.status);
        assertEquals("[]", r.body);
    }

    @Test
    public void nonZeroExit_isNon2xx_neverAbsence() {
        AtomicReference<String> marker = new AtomicReference<>("mist-run7-p5");
        SqlDurableReadback.CommandRunner runner =
                (argv, t) -> new SqlDurableReadback.CommandRunner.Result(1, "", "psql: could not connect");
        DataIntegrityRuntime.HttpResponse r = readback(marker, runner).getSut("/ignored");
        assertEquals("a failed exec must be non-2xx (never read as [] absence)", 0, r.status);
        assertFalse("[]".equals(r.body));
        assertTrue(r.body.contains("could not connect"));
    }

    @Test
    public void thrownExec_isNon2xx_neverAbsence() {
        AtomicReference<String> marker = new AtomicReference<>("mist-run7-p6");
        SqlDurableReadback.CommandRunner runner = (argv, t) -> {
            throw new java.io.IOException("kubectl not found");
        };
        DataIntegrityRuntime.HttpResponse r = readback(marker, runner).getSut("/ignored");
        assertEquals(0, r.status);
        assertFalse("[]".equals(r.body));
    }

    @Test
    public void quotedMarker_isRejectedBeforeAnyCommand() {
        AtomicReference<String> marker = new AtomicReference<>("x' OR '1'='1");
        AtomicReference<Boolean> ran = new AtomicReference<>(false);
        SqlDurableReadback.CommandRunner runner = (argv, t) -> {
            ran.set(true);
            return new SqlDurableReadback.CommandRunner.Result(0, "", "");
        };
        DataIntegrityRuntime.HttpResponse r = readback(marker, runner).getSut("/ignored");
        assertEquals(0, r.status);
        assertFalse("an unsafe marker must never reach the shell", ran.get());
    }

    @Test
    public void noMarkerSupplied_isNon2xx() {
        AtomicReference<String> marker = new AtomicReference<>(null);
        SqlDurableReadback.CommandRunner runner =
                (argv, t) -> new SqlDurableReadback.CommandRunner.Result(0, "", "");
        DataIntegrityRuntime.HttpResponse r = readback(marker, runner).getSut("/ignored");
        assertEquals(0, r.status);
    }
}
