package com.stardustindustry.stardustindustry.machine.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RecipeRunnerEnergyRemainderTest {
    @Test
    void fractionalEnergyAccountCarriesAcrossOperationBoundaries() {
        RecipeEnergyLedger ledger = new RecipeEnergyLedger();

        var firstOperation = ledger.quote(7L);
        assertEquals(0L, firstOperation.joules());
        ledger.commit(firstOperation);

        // Completing or cancelling a recipe does not replace this machine-owned ledger.
        var secondOperation = ledger.quote(13L);
        assertEquals(1L, secondOperation.joules());
        ledger.commit(secondOperation);

        assertEquals(0L, ledger.remainderWattTicks());
    }

    @Test
    void loadedRemainderIsBoundedToOneTickFraction() {
        RecipeEnergyLedger ledger = new RecipeEnergyLedger();
        ledger.load(7L);
        assertEquals(7L, ledger.remainderWattTicks());

        ledger.load(Long.MAX_VALUE);
        assertEquals(19L, ledger.remainderWattTicks());
    }
}
