package com.stardustindustry.stardustindustry.machine.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.stardustindustry.stardustindustry.machine.storage.StorageMode;

class PortModeTest {
    @Test
    void unknownIdsKeepBackwardsCompatibleDefaults() {
        assertEquals(ItemPortMode.BOTH, ItemPortMode.byId(""));
        assertEquals(StorageMode.AUTO, StorageMode.byId("old_world"));
    }

    @Test
    void itemModesExposeOnlyTheirConfiguredDirection() {
        assertTrue(ItemPortMode.BOTH.allowsInsert());
        assertTrue(ItemPortMode.BOTH.allowsExtract());
        assertTrue(ItemPortMode.INPUT.allowsInsert());
        assertFalse(ItemPortMode.INPUT.allowsExtract());
        assertFalse(ItemPortMode.OUTPUT.allowsInsert());
        assertTrue(ItemPortMode.OUTPUT.allowsExtract());
        assertFalse(ItemPortMode.DISABLED.allowsInsert());
        assertFalse(ItemPortMode.DISABLED.allowsExtract());
    }

    @Test
    void storageModesGateChargeAndDischargeIndependently() {
        assertTrue(StorageMode.AUTO.allowsCharge());
        assertTrue(StorageMode.AUTO.allowsDischarge());
        assertTrue(StorageMode.CHARGE_ONLY.allowsCharge());
        assertFalse(StorageMode.CHARGE_ONLY.allowsDischarge());
        assertFalse(StorageMode.DISCHARGE_ONLY.allowsCharge());
        assertTrue(StorageMode.DISCHARGE_ONLY.allowsDischarge());
        assertFalse(StorageMode.DISCONNECTED.allowsCharge());
        assertFalse(StorageMode.DISCONNECTED.allowsDischarge());
    }
}
