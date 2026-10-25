package com.stardustindustry.stardustindustry.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;

class ElectricalTelemetrySnapshotTest {
    @Test
    void gridStateRetainsSignedStorageFlowAndMetering() {
        GridState state = new GridState(true, true, EnergyTier.LV,
                WiringMode.THREE_PHASE_4_WIRE, 6_000L, 9.12, FaultKind.NONE,
                8_000L, -6_000L, 120L, 0.98);

        assertEquals(8_000L, state.requestedPowerWatts());
        assertEquals(-6_000L, state.netPowerWatts());
        assertEquals(120L, state.lineLossWatts());
        assertEquals(0.98, state.efficiency(), 1.0e-9);
    }

    @Test
    void significantChangesBypassTheFiveTickHeartbeat() {
        ElectricalTelemetrySnapshot baseline = snapshot(10_000L, "none", 500);

        assertFalse(snapshot(10_200L, "none", 505).materiallyDiffers(baseline));
        assertTrue(snapshot(11_000L, "none", 505).materiallyDiffers(baseline));
        assertTrue(snapshot(10_000L, "overcurrent", 500).materiallyDiffers(baseline));
        assertTrue(snapshot(10_000L, "none", 520).materiallyDiffers(baseline));
        assertTrue(snapshotWithNetPower(-10_000L).materiallyDiffers(snapshotWithNetPower(10_000L)));
        assertFalse(snapshotWithVoltageDrop(1.28).materiallyDiffers(baseline));
        assertTrue(snapshotWithVoltageDrop(1.50).materiallyDiffers(baseline));
    }

    @Test
    void cableSnapshotExposesOneRunWithLiveMeasurementsAndProductDetails() {
        BlockPos clickedRack = new BlockPos(4, 65, 7);
        CableConnection cable = new CableConnection(
                CableAmpacity.A150,
                CableInsulation.CLASS_2,
                CableColor.ofDyeIndex(14),
                new TerminalEndpoint(clickedRack, 3),
                new TerminalEndpoint(new BlockPos(16, 65, 7), 1));
        EnergizedCable live = new EnergizedCable(
                cable, EnergyTier.MV.volts(), 87.25, 2.75, 245L);

        ElectricalTelemetrySnapshot snapshot = ElectricalTelemetrySnapshot.cableSnapshot(
                clickedRack, cable, live, new CableThermalModel.State(0.25, 8));

        assertEquals(ElectricalTelemetrySnapshot.Subject.CABLE, snapshot.subject());
        assertEquals(clickedRack, snapshot.targetPos());
        assertEquals("item.stardustindustry.cable_150a_class_2", snapshot.nameKey());
        assertTrue(snapshot.energized());
        assertTrue(snapshot.running());
        assertEquals("mv", snapshot.tierId());
        assertEquals("10000", snapshot.wiringId());
        assertEquals("red", snapshot.modeId());
        assertEquals(87.25, snapshot.phaseAAmps(), 1.0e-9);
        assertEquals(245L, snapshot.lineLossWatts());
        assertEquals(2.75, snapshot.voltageDropVolts(), 1.0e-9);
        assertEquals(65.0, snapshot.maximumCableTemperatureC(), 1.0e-9);
        assertEquals(120.0, snapshot.cableAmpacityAmps(), 1.0e-9);
    }

    @Test
    void streamCodecRetainsVoltageDrop() {
        ElectricalTelemetrySnapshot expected = snapshot(10_000L, "none", 500);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
        try {
            ElectricalTelemetrySnapshot.STREAM_CODEC.encode(buffer, expected);

            assertEquals(expected, ElectricalTelemetrySnapshot.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static ElectricalTelemetrySnapshot snapshot(long delivered, String fault, int progress) {
        return new ElectricalTelemetrySnapshot(BlockPos.ZERO, "block.stardustindustry.crusher",
                ElectricalTelemetrySnapshot.Subject.CRUSHER, true, true, true, true,
                "lv", WiringMode.THREE_PHASE_4_WIRE.id(), fault, "",
                12_000L, delivered, delivered, 15.0, 15.0, 15.0, 0.0,
                100L, 1.25, 0.98, 35.0, 150.0, 5_000L, 10_000L, 5L, progress);
    }

    private static ElectricalTelemetrySnapshot snapshotWithNetPower(long netPower) {
        ElectricalTelemetrySnapshot base = snapshot(10_000L, "none", 500);
        return copyWithMetering(base, netPower, base.voltageDropVolts());
    }

    private static ElectricalTelemetrySnapshot snapshotWithVoltageDrop(double voltageDropVolts) {
        ElectricalTelemetrySnapshot base = snapshot(10_000L, "none", 500);
        return copyWithMetering(base, base.netPowerWatts(), voltageDropVolts);
    }

    private static ElectricalTelemetrySnapshot copyWithMetering(
            ElectricalTelemetrySnapshot base, long netPower, double voltageDropVolts) {
        return new ElectricalTelemetrySnapshot(base.targetPos(), base.nameKey(), base.subject(),
                base.energized(), base.switchClosed(), base.formed(), base.running(), base.tierId(),
                base.wiringId(), base.faultId(), base.modeId(), base.requestedPowerWatts(),
                base.deliveredPowerWatts(), netPower, base.phaseAAmps(), base.phaseBAmps(),
                base.phaseCAmps(), base.neutralAmps(), base.lineLossWatts(),
                voltageDropVolts, base.efficiency(), base.maximumCableTemperatureC(),
                base.cableAmpacityAmps(), base.storedJoules(),
                base.capacityJoules(), base.estimatedSeconds(), base.progressPermille());
    }
}
