package com.stardustindustry.stardustindustry.machine.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.stardustindustry.stardustindustry.machine.module.ItemInventoryModule.SlotGroup;
import com.stardustindustry.stardustindustry.machine.port.ItemPortMode;

class ItemInventoryAutomationTest {
    @Test
    void automationCannotExtractInputsOrInsertOutputs() {
        assertTrue(ItemInventoryModule.permitsAutomationInsert(SlotGroup.INPUT, ItemPortMode.BOTH));
        assertFalse(ItemInventoryModule.permitsAutomationExtract(SlotGroup.INPUT, ItemPortMode.BOTH));
        assertFalse(ItemInventoryModule.permitsAutomationInsert(SlotGroup.OUTPUT, ItemPortMode.BOTH));
        assertTrue(ItemInventoryModule.permitsAutomationExtract(SlotGroup.OUTPUT, ItemPortMode.BOTH));
    }

    @Test
    void directionalModesHideSlotsFromTheOppositeDirection() {
        SlotGroup[] groups = {
                SlotGroup.INPUT, SlotGroup.OUTPUT, SlotGroup.UPGRADE, SlotGroup.INTERNAL
        };

        assertArrayEquals(new int[] {0, 1},
                ItemInventoryModule.automationSlots(groups, ItemPortMode.BOTH));
        assertArrayEquals(new int[] {0},
                ItemInventoryModule.automationSlots(groups, ItemPortMode.INPUT));
        assertArrayEquals(new int[] {1},
                ItemInventoryModule.automationSlots(groups, ItemPortMode.OUTPUT));
        assertEquals(0, ItemInventoryModule.automationSlots(groups, ItemPortMode.DISABLED).length);
    }

}
