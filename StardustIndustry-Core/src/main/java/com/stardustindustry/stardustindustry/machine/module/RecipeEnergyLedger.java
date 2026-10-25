package com.stardustindustry.stardustindustry.machine.module;

import com.stardustindustry.stardustindustry.energy.ElectricalUnits;

/** Fractional joule accounting owned by a machine rather than by one recipe operation. */
final class RecipeEnergyLedger {
    private long remainderWattTicks;

    ElectricalUnits.TickEnergy quote(long powerWatts) {
        return ElectricalUnits.tickEnergy(Math.max(0L, powerWatts), remainderWattTicks);
    }

    void commit(ElectricalUnits.TickEnergy energy) {
        if (energy != null) {
            remainderWattTicks = energy.remainderWattTicks();
        }
    }

    void load(long savedRemainderWattTicks) {
        remainderWattTicks = Math.clamp(savedRemainderWattTicks, 0L,
                ElectricalUnits.TICKS_PER_SECOND - 1L);
    }

    long remainderWattTicks() {
        return remainderWattTicks;
    }
}
