package com.stardustindustry.stardustindustry.energy.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

class FuelBurnStateTest {
    @Test
    void extractionConsumesExactlyTheRequestedJoules() {
        FuelBurnState fuel = new FuelBurnState();
        assertTrue(fuel.addBurnTicks(10, 2_000L));
        assertEquals(1_000L, fuel.storedJoules());

        assertEquals(125L, fuel.extractJoules(125L, true));
        assertEquals(1_000L, fuel.storedJoules(), "simulation must be side-effect free");
        assertEquals(125L, fuel.extractJoules(125L, false));
        assertEquals(875L, fuel.storedJoules());
        assertEquals(875L, fuel.extractJoules(2_000L, false));
        assertFalse(fuel.hasFuel());
    }

    @Test
    void fractionalWattTicksCarryAcrossFuelItemsAndReload() {
        FuelBurnState beforeReload = new FuelBurnState();
        assertTrue(beforeReload.addBurnTicks(1, 33L));
        assertEquals(1L, beforeReload.storedJoules());

        FuelBurnState afterReload = new FuelBurnState();
        afterReload.load(beforeReload.save(), 33L);
        assertTrue(afterReload.addBurnTicks(1, 33L));
        assertEquals(3L, afterReload.storedJoules());
    }

    @Test
    void legacyBurnTicksMigrateAtTheGeneratorRating() {
        FuelBurnState fuel = new FuelBurnState();
        fuel.load(3, 2);
        assertEquals(5_000L, fuel.storedJoules());
    }

    @Test
    void legacyMigrationKeepsBothFullIntegerTickCounters() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("QueuedTicks", Integer.MAX_VALUE);
        legacy.putInt("BurningTicks", Integer.MAX_VALUE);

        FuelBurnState fuel = new FuelBurnState();
        fuel.load(legacy, FuelBurnState.LEGACY_GENERATOR_WATTS);

        assertEquals(4_294_967_294_000L, fuel.storedJoules());
    }
}
