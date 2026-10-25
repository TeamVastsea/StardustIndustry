package com.stardustindustry.stardustindustry.machine.storage;

import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.machine.MachineTier;

/** Electrical and storage ratings shared by a station controller and its cells. */
public enum StorageTier {
    LV(EnergyTier.LV, MachineTier.LV, 100, 100),
    MV(EnergyTier.MV, MachineTier.MV, 400, 250),
    HV(EnergyTier.HV, MachineTier.HV, 1_600, 500),
    EHV(EnergyTier.EHV, MachineTier.EHV, 6_000, 1_000);

    private final EnergyTier energyTier;
    private final MachineTier machineTier;
    private final long capacityPerCellKilowattHours;
    private final int amperage;

    StorageTier(EnergyTier energyTier, MachineTier machineTier, long capacityPerCellKilowattHours, int amperage) {
        this.energyTier = energyTier;
        this.machineTier = machineTier;
        this.capacityPerCellKilowattHours = capacityPerCellKilowattHours;
        this.amperage = amperage;
    }

    public EnergyTier energyTier() {
        return energyTier;
    }

    public MachineTier machineTier() {
        return machineTier;
    }

    public long capacityPerCellKilowattHours() {
        return capacityPerCellKilowattHours;
    }

    public int amperage() {
        return amperage;
    }

    public long nominalPowerWatts() {
        com.stardustindustry.stardustindustry.energy.WiringMode wiring = energyTier == EnergyTier.LV
                ? com.stardustindustry.stardustindustry.energy.WiringMode.THREE_PHASE_4_WIRE
                : com.stardustindustry.stardustindustry.energy.WiringMode.THREE_PHASE_3_WIRE;
        return wiring.powerWatts(amperage, energyTier);
    }

    /** Saturating capacity arithmetic protects worlds if size rules change later. */
    public long capacityJoulesForCells(int cells) {
        long kwh = Math.multiplyExact(Math.max(0, cells), capacityPerCellKilowattHours);
        return com.stardustindustry.stardustindustry.energy.ElectricalUnits.kilowattHoursToJoules(kwh);
    }
}
