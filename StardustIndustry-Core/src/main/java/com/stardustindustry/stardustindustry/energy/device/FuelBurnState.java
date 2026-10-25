package com.stardustindustry.stardustindustry.energy.device;

import com.stardustindustry.stardustindustry.energy.ElectricalUnits;

import net.minecraft.nbt.CompoundTag;

/** Fuel energy stored as exact whole joules, with a sub-joule watt-tick remainder. */
final class FuelBurnState {
    static final long LEGACY_GENERATOR_WATTS = 20_000L;

    private long storedJoules;
    private long remainderWattTicks;

    boolean addBurnTicks(int ticks, long ratedPowerWatts) {
        return addBurnTicks((long) ticks, ratedPowerWatts);
    }

    private boolean addBurnTicks(long ticks, long ratedPowerWatts) {
        if (ticks <= 0 || ratedPowerWatts <= 0L) {
            return false;
        }
        long wholePerTick = ratedPowerWatts / ElectricalUnits.TICKS_PER_SECOND;
        long fractionalPerTick = ratedPowerWatts % ElectricalUnits.TICKS_PER_SECOND;
        long wholeJoules = saturatedMultiply(wholePerTick, ticks);
        long fractionalJoules = saturatedMultiply(
                fractionalPerTick, ticks / ElectricalUnits.TICKS_PER_SECOND);
        long tailWattTicks = fractionalPerTick * (ticks % ElectricalUnits.TICKS_PER_SECOND)
                + remainderWattTicks;
        fractionalJoules = saturatedAdd(fractionalJoules,
                tailWattTicks / ElectricalUnits.TICKS_PER_SECOND);
        remainderWattTicks = tailWattTicks % ElectricalUnits.TICKS_PER_SECOND;
        storedJoules = saturatedAdd(storedJoules, saturatedAdd(wholeJoules, fractionalJoules));
        return true;
    }

    /** Legacy convenience for the fixed 20 kW generator rating. */
    boolean addTicks(int ticks) {
        return addBurnTicks(ticks, LEGACY_GENERATOR_WATTS);
    }

    boolean hasFuel() {
        return storedJoules > 0L;
    }

    long storedJoules() {
        return storedJoules;
    }

    long extractJoules(long amount, boolean simulate) {
        long extracted = Math.min(Math.max(0L, amount), storedJoules);
        if (!simulate && extracted > 0L) {
            storedJoules -= extracted;
        }
        return extracted;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Joules", storedJoules);
        tag.putLong("RemainderWattTicks", remainderWattTicks);
        return tag;
    }

    void load(CompoundTag tag, long ratedPowerWatts) {
        storedJoules = Math.max(0L, tag.getLong("Joules"));
        remainderWattTicks = Math.max(0L, Math.min(ElectricalUnits.TICKS_PER_SECOND - 1L,
                tag.getLong("RemainderWattTicks")));
        if (!tag.contains("Joules")
                && (tag.contains("QueuedTicks") || tag.contains("BurningTicks"))) {
            long legacyTicks = Math.max(0L, tag.getInt("QueuedTicks"))
                    + Math.max(0L, tag.getInt("BurningTicks"));
            addBurnTicks(legacyTicks, ratedPowerWatts);
        }
    }

    /** Migration entry point for worlds written by the old tick queue. */
    void load(int queuedTicks, int burningTicks) {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("QueuedTicks", queuedTicks);
        legacy.putInt("BurningTicks", burningTicks);
        load(legacy, LEGACY_GENERATOR_WATTS);
    }

    /** Temporary compatibility while the block entity migrates to the FuelEnergy tag. */
    int queuedTicks() {
        return (int) Math.min(Integer.MAX_VALUE,
                storedJoules / (LEGACY_GENERATOR_WATTS / ElectricalUnits.TICKS_PER_SECOND));
    }

    int burningTicks() {
        return 0;
    }

    /** Temporary compatibility for callers compiled against the old API. */
    boolean extract(boolean simulate, Long gameTick) {
        return extractJoules(LEGACY_GENERATOR_WATTS / ElectricalUnits.TICKS_PER_SECOND, simulate) > 0L;
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long saturatedMultiply(long value, long multiplier) {
        return value > 0L && multiplier > Long.MAX_VALUE / value
                ? Long.MAX_VALUE : value * multiplier;
    }
}
