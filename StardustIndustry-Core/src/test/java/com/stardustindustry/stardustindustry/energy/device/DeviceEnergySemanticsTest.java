package com.stardustindustry.stardustindustry.energy.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.stardustindustry.stardustindustry.energy.ElectricalUnits;
import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.energy.WiringMode;

class DeviceEnergySemanticsTest {
    @Test
    void fuelSimulationDoesNotConsumeStoredJoules() {
        FuelBurnState fuel = new FuelBurnState();
        assertTrue(fuel.addBurnTicks(100, ElectricalDeviceKind.FUEL_GENERATOR.ratedPowerWatts()));
        assertEquals(100_000L, fuel.storedJoules());

        assertEquals(137L, fuel.extractJoules(137L, true));
        assertEquals(100_000L, fuel.storedJoules());

        assertEquals(137L, fuel.extractJoules(137L, false));
        assertEquals(99_863L, fuel.storedJoules());
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
    void onlyConvertersOwnPersistentDeviceBuffers() {
        for (ElectricalDeviceKind kind : ElectricalDeviceKind.values()) {
            long capacity = DeviceEnergyPolicy.persistentCapacity(kind);
            if (kind.converter()) {
                assertEquals(kind.ratedPowerWatts() * 2L, capacity, kind.name());
            } else {
                assertEquals(0L, capacity, kind.name());
            }
        }
    }

    @Test
    void passThroughDevicesDoNotRequestPowerWhileDownstreamIsIdle() {
        for (ElectricalDeviceKind kind : ElectricalDeviceKind.values()) {
            if (!kind.transformer() && !kind.protection()) continue;
            assertTrue(DeviceEnergyPolicy.isPassThrough(kind), kind.name());
            assertEquals(0L, DeviceEnergyPolicy.inputPowerForOutput(kind, 0L), kind.name());
            assertEquals((ProtectionModel.maximumThroughputWatts(kind) + 19L) / 20L,
                    DeviceEnergyPolicy.passThroughJoulesPerTick(kind), kind.name());
        }
    }

    @Test
    void transformerPassThroughAccountsForEfficiencyWithoutLongTermStorage() {
        ElectricalDeviceKind transformer = ElectricalDeviceKind.TRANSFORMER_LV_MV;

        assertEquals(23_520L, DeviceEnergyPolicy.maxPassThroughOutputWatts(transformer));
        assertEquals(1_021L, DeviceEnergyPolicy.inputPowerForOutput(transformer, 1_000L));
        assertEquals(24_000L, DeviceEnergyPolicy.inputPowerForOutput(transformer, 23_520L));
        assertEquals(0L, DeviceEnergyPolicy.persistentCapacity(transformer));
        assertEquals(1L, DeviceEnergyPolicy.roundingCarryJoules(transformer));
    }

    @Test
    void protectionPassThroughIsLosslessAndLimitedToOneTick() {
        ElectricalDeviceKind breaker = ElectricalDeviceKind.LV_BREAKER;

        long maximumThroughput = ProtectionModel.maximumThroughputWatts(breaker);
        assertEquals(maximumThroughput,
                DeviceEnergyPolicy.maxPassThroughOutputWatts(breaker));
        assertEquals(12_345L, DeviceEnergyPolicy.inputPowerForOutput(breaker, 12_345L));
        assertEquals(maximumThroughput,
                DeviceEnergyPolicy.inputPowerForOutput(breaker, Long.MAX_VALUE));
        assertEquals((maximumThroughput + 19L) / 20L,
                DeviceEnergyPolicy.passThroughJoulesPerTick(breaker));
        assertEquals(0L, DeviceEnergyPolicy.persistentCapacity(breaker));
        assertEquals(0L, DeviceEnergyPolicy.roundingCarryJoules(breaker));
    }

    @Test
    void feConversionCarriesSubJouleOutputAcrossCalls() {
        long remainder = 0L;
        long converted = 0L;

        for (int call = 0; call < 20; call++) {
            EnergyConversion.Result result = EnergyConversion.convertInput(
                    1L, ElectricalDeviceKind.LV_FE_CONVERTER.efficiency(), remainder);
            converted += result.amount();
            remainder = result.remainder();
        }

        assertEquals(19L, converted);
        assertEquals(0L, remainder);
    }

    @Test
    void transformerOutputReusesPriorRoundingCredit() {
        long remainder = 0L;
        long consumed = 0L;

        for (int call = 0; call < 49; call++) {
            EnergyConversion.Result result = EnergyConversion.consumeInputForOutput(
                    1L, ElectricalDeviceKind.TRANSFORMER_LV_MV.efficiency(), remainder);
            consumed += result.amount();
            remainder = result.remainder();
        }

        assertEquals(50L, consumed);
        assertEquals(0L, remainder);
    }

    @Test
    void feOutputReusesPriorRoundingCredit() {
        long remainder = 0L;
        long consumed = 0L;

        for (int call = 0; call < 19; call++) {
            EnergyConversion.Result result = EnergyConversion.consumeInputForOutput(
                    1L, ElectricalDeviceKind.LV_FE_CONVERTER.efficiency(), remainder);
            consumed += result.amount();
            remainder = result.remainder();
        }

        assertEquals(20L, consumed);
        assertEquals(0L, remainder);
    }

    @Test
    void conversionPreviewDoesNotMutateCallerRemainder() {
        EnergyConversion.Result first = EnergyConversion.consumeInputForOutput(1L, 0.98, 0L);
        EnergyConversion.Result repeated = EnergyConversion.consumeInputForOutput(1L, 0.98, 0L);

        assertEquals(first, repeated);
        assertEquals(2L, first.amount());
        assertEquals(48L, first.remainder());
    }

    @Test
    void convertedInputRespectsFractionalBufferCapacity() {
        assertEquals(2L, EnergyConversion.maxInputForOutputCapacity(1L, 0.95, 0L));
        EnergyConversion.Result first = EnergyConversion.convertInput(1L, 0.95, 0L);
        assertEquals(1L, EnergyConversion.maxInputForOutputCapacity(
                1L, 0.95, first.remainder()));
        EnergyConversion.Result second = EnergyConversion.convertInput(
                1L, 0.95, first.remainder());
        assertEquals(1L, second.amount());
        assertEquals(18L, second.remainder());

        assertEquals(0L, EnergyConversion.maxInputForOutputCapacity(0L, 0.95,
                second.remainder()));
        assertEquals(Long.MAX_VALUE, EnergyConversion.maxInputForOutputCapacity(
                Long.MAX_VALUE, 0.95, second.remainder()));
    }

    @Test
    void conversionArithmeticSaturatesWithoutLongOverflow() {
        assertEquals(Long.MAX_VALUE, EnergyConversion.outputForInput(Long.MAX_VALUE, 1.0));
        assertEquals(Long.MAX_VALUE, EnergyConversion.inputForOutput(Long.MAX_VALUE, 0.95));
        assertEquals(Long.MAX_VALUE, EnergyConversion.maxInputForOutputCapacity(
                Long.MAX_VALUE, 1.0, 0L));
        assertEquals(Long.MAX_VALUE, EnergyConversion.maxInputForOutputCapacity(
                Long.MAX_VALUE, 0.95, 19L));
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
    void currentRatedDevicesUseBalancedThreePhasePower() {
        for (ElectricalDeviceKind kind : ElectricalDeviceKind.values()) {
            if (kind.ratedCurrentAmps() <= 0) continue;
            WiringMode wiring = kind.lowTier() == EnergyTier.LV
                    ? WiringMode.THREE_PHASE_4_WIRE : WiringMode.THREE_PHASE_3_WIRE;
            double current = wiring.lineCurrentAmps(kind.ratedPowerWatts(), kind.lowTier());
            assertEquals(kind.ratedCurrentAmps(), current, 0.001, kind.name());
        }
        assertEquals(98_727L, ElectricalDeviceKind.LV_BREAKER.ratedPowerWatts());
        assertEquals(65_818L, ElectricalDeviceKind.LV_FE_CONVERTER.ratedPowerWatts());
    }

    @Test
    void emptyFuelCannotBeExtracted() {
        assertEquals(0L, new FuelBurnState().extractJoules(1L, true));
    }
}
