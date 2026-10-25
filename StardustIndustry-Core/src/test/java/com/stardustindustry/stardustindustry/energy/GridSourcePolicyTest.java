package com.stardustindustry.stardustindustry.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

class GridSourcePolicyTest {
    @Test
    void chargedStorageEstablishesAStandaloneIsland() {
        TestPort storage = new TestPort(ElectricalPortRole.STORAGE, true, 1_000L);
        TestPort load = new TestPort(ElectricalPortRole.LOAD, true, 0L);

        GridSourcePolicy.Selection selection = GridSourcePolicy.select(List.of(storage, load));

        assertEquals(GridSourcePolicy.Kind.STORAGE_ISLAND, selection.kind());
        assertSame(storage, selection.voltageSource());
    }

    @Test
    void emptyStorageDoesNotEnergizeAnIsland() {
        GridSourcePolicy.Selection selection = GridSourcePolicy.select(List.of(
                new TestPort(ElectricalPortRole.STORAGE, true, 0L),
                new TestPort(ElectricalPortRole.LOAD, true, 0L)));

        assertEquals(GridSourcePolicy.Kind.NONE, selection.kind());
    }

    @Test
    void dedicatedSourceTakesPriorityAndTwoDedicatedSourcesConflict() {
        TestPort source = new TestPort(ElectricalPortRole.SOURCE, true, 1_000L);
        TestPort storage = new TestPort(ElectricalPortRole.STORAGE, true, 1_000L);
        assertEquals(GridSourcePolicy.Kind.DEDICATED_SOURCE,
                GridSourcePolicy.select(List.of(storage, source)).kind());
        assertEquals(GridSourcePolicy.Kind.CONFLICT,
                GridSourcePolicy.select(List.of(source,
                        new TestPort(ElectricalPortRole.SOURCE, true, 1_000L))).kind());
    }

    private record TestPort(ElectricalPortRole electricalRole, boolean enabled,
                            long availablePowerWatts) implements IElectricalPort {
        @Override public EnergyTier tier() { return EnergyTier.LV; }
        @Override public WiringMode wiring() { return WiringMode.THREE_PHASE_4_WIRE; }
        @Override public long maxPowerWatts() { return availablePowerWatts; }
    }
}
