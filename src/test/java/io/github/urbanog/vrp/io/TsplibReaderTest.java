package io.github.urbanog.vrp.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.urbanog.vrp.model.DistanceType;
import io.github.urbanog.vrp.model.Instance;
import org.junit.jupiter.api.Test;

class TsplibReaderTest {
    static final String SMALL = """
            NAME : tiny
            COMMENT : hand-written test instance
            TYPE : CVRP
            DIMENSION : 4
            EDGE_WEIGHT_TYPE : EUC_2D
            CAPACITY : 10
            NODE_COORD_SECTION
             1 0 0
             2 3 4
             3 -3 4
             4 0 -5
            DEMAND_SECTION
            1 0
            2 4
            3 5
            4 6
            DEPOT_SECTION
             1
             -1
            EOF
            """;

    @Test
    void parsesStandardFile() {
        Instance inst = TsplibReader.parse(SMALL);
        assertEquals("tiny", inst.name());
        assertEquals(10, inst.capacity());
        assertEquals(DistanceType.EUCLIDEAN_ROUNDED, inst.distanceType());
        assertEquals(3, inst.size());
        assertEquals(15, inst.totalDemand());
        assertEquals(2, inst.minVehicles());
        assertEquals(2, inst.customers().get(0).id());
        assertTrue(inst.validate().isEmpty());
    }

    @Test
    void acceptsKeyColonWithoutSpaces() {
        Instance inst = TsplibReader.parse(SMALL.replace("CAPACITY : 10", "CAPACITY: 12"));
        assertEquals(12, inst.capacity());
    }

    @Test
    void writeThenReadIsIdentity() {
        Instance inst = InstanceGenerator.generate("rt", 40, 8, InstanceGenerator.Layout.CLUSTERED, 3);
        assertEquals(inst, TsplibReader.parse(TsplibReader.write(inst)));
    }

    @Test
    void reportsLineNumbersAndMissingData() {
        VrpFormatException e = assertThrows(VrpFormatException.class,
                () -> TsplibReader.parse(SMALL.replace(" 3 -3 4", " 3 -3 x")));
        assertEquals(10, e.line());
        assertThrows(VrpFormatException.class, () -> TsplibReader.parse(SMALL.replace("4 6\n", "")));
        assertThrows(VrpFormatException.class, () -> TsplibReader.parse(SMALL.replace("CAPACITY : 10\n", "")));
        assertThrows(VrpFormatException.class, () -> TsplibReader.parse(SMALL.replace("EUC_2D", "GEO")));
        assertThrows(VrpFormatException.class, () -> TsplibReader.parse(SMALL.replace("DIMENSION : 4", "DIMENSION : 5")));
    }

    @Test
    void bundledInstancesLoadAndAreValid() {
        var catalog = InstanceCatalog.loadBundled();
        assertEquals(InstanceCatalog.BUNDLED.size(), catalog.all().size());
        for (Instance inst : catalog.all()) {
            assertTrue(inst.validate().isEmpty(), inst.name());
        }
    }

    @Test
    void roundedDistanceFollowsTsplibNint() {
        var a = new io.github.urbanog.vrp.model.Point(0, 0);
        assertEquals(2.0, DistanceType.EUCLIDEAN_ROUNDED.cost(a, new io.github.urbanog.vrp.model.Point(1.5, 0)), 0.0);
        assertEquals(1.0, DistanceType.EUCLIDEAN_ROUNDED.cost(a, new io.github.urbanog.vrp.model.Point(1, 1)), 0.0);
        assertEquals(Math.sqrt(2), DistanceType.EUCLIDEAN.cost(a, new io.github.urbanog.vrp.model.Point(1, 1)), 1e-12);
    }
}
