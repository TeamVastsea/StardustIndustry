package com.stardustindustry.stardustindustry.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ElectricalFaultSafetyTest {
    private static final int CONFIRMATION_TICKS = 40;

    @Test
    void delayedFaultTriggersOnTickFortyButNotTickThirtyNine() {
        DelayedFault fault = new DelayedFault(CONFIRMATION_TICKS);

        for (int tick = 1; tick < CONFIRMATION_TICKS; tick++) {
            assertFalse(fault.update(true), "fault triggered early on tick " + tick);
            assertEquals(tick, fault.observedTicks());
            assertFalse(fault.latched());
        }

        assertTrue(fault.update(true));
        assertEquals(CONFIRMATION_TICKS, fault.observedTicks());
        assertTrue(fault.latched());
        assertFalse(fault.update(true), "a latched fault must not trigger again");
    }

    @Test
    void interruptedConditionClearsTheConfirmationWindow() {
        DelayedFault fault = new DelayedFault(CONFIRMATION_TICKS);

        holdFaultFor(fault, CONFIRMATION_TICKS - 1);
        assertFalse(fault.update(false));
        assertEquals(0, fault.observedTicks());
        assertFalse(fault.latched());

        holdFaultFor(fault, CONFIRMATION_TICKS - 1);
        assertFalse(fault.latched(), "ticks before the interruption must not carry over");
        assertTrue(fault.update(true));
    }

    @Test
    void missingPhaseRequiresPowerAndAClosedSource() {
        assertFaultConfirmationGate(FaultKind.MISSING_PHASE);
    }

    @Test
    void overvoltageRequiresPowerAndAClosedSource() {
        assertFaultConfirmationGate(FaultKind.OVERVOLTAGE);
    }

    @Test
    void sharedDestructiveTrackerConfirmsBothFaultsAtTheFortyTickBoundary() {
        DestructiveFaultTracker missingPhase = new DestructiveFaultTracker();
        GridState missingState = state(true, true, FaultKind.MISSING_PHASE);
        for (int tick = 1; tick < DestructiveFaultTracker.CONFIRMATION_TICKS; tick++) {
            assertNull(missingPhase.update(missingState, EnergyTier.LV));
        }
        assertEquals(new DestructiveFaultTracker.Confirmation(FaultKind.MISSING_PHASE, 2.0f),
                missingPhase.update(missingState, EnergyTier.LV));

        DestructiveFaultTracker overvoltage = new DestructiveFaultTracker();
        GridState overvoltageState = new GridState(true, true, EnergyTier.HV,
                WiringMode.THREE_PHASE_3_WIRE, 0L, 0.0, FaultKind.OVERVOLTAGE);
        for (int tick = 1; tick < DestructiveFaultTracker.CONFIRMATION_TICKS; tick++) {
            assertNull(overvoltage.update(overvoltageState, EnergyTier.LV));
        }
        assertEquals(new DestructiveFaultTracker.Confirmation(FaultKind.OVERVOLTAGE, 6.0f),
                overvoltage.update(overvoltageState, EnergyTier.LV));
    }

    @Test
    void sharedDestructiveTrackerResetsWhenTheInputIsDeenergized() {
        DestructiveFaultTracker faults = new DestructiveFaultTracker();
        GridState energized = state(true, true, FaultKind.MISSING_PHASE);
        for (int tick = 1; tick < DestructiveFaultTracker.CONFIRMATION_TICKS; tick++) {
            assertNull(faults.update(energized, EnergyTier.LV));
        }

        assertNull(faults.update(state(false, true, FaultKind.MISSING_PHASE), EnergyTier.LV));
        for (int tick = 1; tick < DestructiveFaultTracker.CONFIRMATION_TICKS; tick++) {
            assertNull(faults.update(energized, EnergyTier.LV));
        }
        assertEquals(new DestructiveFaultTracker.Confirmation(FaultKind.MISSING_PHASE, 2.0f),
                faults.update(energized, EnergyTier.LV));
    }

    @Test
    void destructiveFaultConfirmationSurvivesReload() {
        DestructiveFaultTracker beforeReload = new DestructiveFaultTracker();
        GridState energized = state(true, true, FaultKind.MISSING_PHASE);
        for (int tick = 1; tick < DestructiveFaultTracker.CONFIRMATION_TICKS; tick++) {
            assertNull(beforeReload.update(energized, EnergyTier.LV));
        }

        DestructiveFaultTracker afterReload = new DestructiveFaultTracker();
        afterReload.load(beforeReload.save());

        assertEquals(new DestructiveFaultTracker.Confirmation(FaultKind.MISSING_PHASE, 2.0f),
                afterReload.update(energized, EnergyTier.LV));
    }

    private static void assertFaultConfirmationGate(FaultKind fault) {
        assertTrue(ElectricalFaultPolicy.shouldConfirm(state(true, true, fault), fault));
        assertFalse(ElectricalFaultPolicy.shouldConfirm(state(false, true, fault), fault),
                "an unpowered circuit must never confirm " + fault);
        assertFalse(ElectricalFaultPolicy.shouldConfirm(state(true, false, fault), fault),
                "an open source must never confirm " + fault);
        assertFalse(ElectricalFaultPolicy.shouldConfirm(state(false, false, fault), fault));
        assertFalse(ElectricalFaultPolicy.shouldConfirm(state(true, true, FaultKind.NONE), fault),
                "a different grid state must not confirm " + fault);
    }

    private static GridState state(boolean energized, boolean sourceSwitchClosed, FaultKind fault) {
        return new GridState(energized, sourceSwitchClosed, EnergyTier.LV,
                WiringMode.THREE_PHASE_4_WIRE, 0L, 0.0, fault);
    }

    private static void holdFaultFor(DelayedFault fault, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            assertFalse(fault.update(true));
        }
    }
}
