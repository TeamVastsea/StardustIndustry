package com.stardustindustry.stardustindustry.energy.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.stardustindustry.stardustindustry.energy.ElectricalUnits;
import com.stardustindustry.stardustindustry.energy.EnergyTier;

class DeviceEnergySemanticsTest {
    @Test
    void fuelSimulationDoesNotMoveOrConsumeQueuedFuel() {
        FuelBurnState fuel = new FuelBurnState();
        assertTrue(fuel.addTicks(100));

        assertTrue(fuel.extract(true, 10L));
        assertEquals(100, fuel.queuedTicks());
        assertEquals(0, fuel.burningTicks());

        assertTrue(fuel.extract(false, 10L));
        assertEquals(0, fuel.queuedTicks());
        assertEquals(99, fuel.burningTicks());
        assertTrue(fuel.extract(false, 10L));
        assertEquals(99, fuel.burningTicks(), "fuel burns at most once in a game tick");
        assertTrue(fuel.extract(false, 11L));
        assertEquals(98, fuel.burningTicks());
    }

    @Test
    void converterAppliesEfficiencyExactlyOnceInEitherDirection() {
        ElectricalDeviceKind converter = ElectricalDeviceKind.LV_FE_CONVERTER;
        long nativeStoredFromFe = EnergyConversion.outputForInput(100L, converter.efficiency());
        long nativeGridOutput = EnergyConversion.outputForInput(nativeStoredFromFe,
                converter.nativeOutputEfficiency(true));
        long feOutputFromNative = EnergyConversion.outputForInput(100L, converter.efficiency());

        assertEquals(95L, nativeGridOutput);
        assertEquals(95L, feOutputFromNative);
    }

    @Test
    void transformersFormTheCompleteTierChain() {
        assertEquals(EnergyTier.LV, ElectricalDeviceKind.TRANSFORMER_LV_MV.lowTier());
        assertEquals(EnergyTier.MV, ElectricalDeviceKind.TRANSFORMER_LV_MV.highTier());
        assertEquals(EnergyTier.MV, ElectricalDeviceKind.TRANSFORMER_MV_HV.lowTier());
        assertEquals(EnergyTier.HV, ElectricalDeviceKind.TRANSFORMER_MV_HV.highTier());
        assertEquals(EnergyTier.HV, ElectricalDeviceKind.TRANSFORMER_HV_EHV.lowTier());
        assertEquals(EnergyTier.EHV, ElectricalDeviceKind.TRANSFORMER_HV_EHV.highTier());
    }

    @Test
    void repeatedCapabilityCallsShareOneRatedTickBudget() {
        PerTickPowerLimit limit = new PerTickPowerLimit();
        assertEquals(100, limit.limit(150, 2_000L, 5L));
        limit.record(80, 5L);
        assertEquals(20, limit.limit(150, 2_000L, 5L));
        assertEquals(100, limit.limit(150, 2_000L, 6L));
    }

    @Test
    void currentRatedDevicesUseDcEquivalentPower() {
        for (ElectricalDeviceKind kind : ElectricalDeviceKind.values()) {
            if (kind.ratedCurrentAmps() <= 0) continue;
            double current = ElectricalUnits.currentAmps(kind.ratedPowerWatts(),
                    kind.lowTier().volts());
            assertEquals(kind.ratedCurrentAmps(), current, 0.001, kind.name());
        }
        assertEquals(57_000L, ElectricalDeviceKind.LV_BREAKER.ratedPowerWatts());
        assertEquals(38_000L, ElectricalDeviceKind.LV_FE_CONVERTER.ratedPowerWatts());
    }

    @Test
    void emptyFuelCannotBeExtracted() {
        assertFalse(new FuelBurnState().extract(true, 0L));
    }
}
