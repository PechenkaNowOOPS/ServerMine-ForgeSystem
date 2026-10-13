package ru.servermine.forge;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MenuInputTransferTest {
    @Test void playerInventorySideDoesNotDependOnWrapperObjectIdentity() {
        UUID player=UUID.randomUUID();
        assertTrue(MenuInputTransfer.playerInventorySide("PLAYER","PLAYER",player,player));
        assertFalse(MenuInputTransfer.playerInventorySide("CHEST","PLAYER",player,player));
        assertFalse(MenuInputTransfer.playerInventorySide("PLAYER","PLAYER",UUID.randomUUID(),player));
        assertFalse(MenuInputTransfer.playerInventorySide(null,"PLAYER",player,player));
    }

    @Test void onlyShiftMoveFromVisiblePlayerStorageRoutesIntoMenu() {
        assertEquals(9, MenuInputTransfer.sourceSlot(true, 54, 9, 9, 54,
                ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertEquals(35, MenuInputTransfer.sourceSlot(true, 80, 35, 35, 54,
                ClickType.SHIFT_RIGHT, InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertEquals(0, MenuInputTransfer.sourceSlot(true, 81, 0, 0, 54,
                ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertEquals(8, MenuInputTransfer.sourceSlot(true, 89, 8, 8, 54,
                ClickType.SHIFT_RIGHT, InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertEquals(-1, MenuInputTransfer.sourceSlot(false, 54, 0, 0, 54,
                ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertEquals(-1, MenuInputTransfer.sourceSlot(true, 53, 53, 53, 54,
                ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertEquals(-1, MenuInputTransfer.sourceSlot(true, 90, 36, 36, 54,
                ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertEquals(-1, MenuInputTransfer.sourceSlot(true, 54, 10, 9, 54,
                ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertEquals(-1, MenuInputTransfer.sourceSlot(true, 54, 0, 0, 54,
                ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertEquals(-1, MenuInputTransfer.sourceSlot(true, 54, 0, 0, 54,
                ClickType.SHIFT_LEFT, InventoryAction.HOTBAR_SWAP));
    }

    @Test void inputCategoriesAreExclusiveAndRejectUnsupportedItems() {
        assertEquals(0, MenuInputTransfer.target(true, false, false));
        assertEquals(1, MenuInputTransfer.target(false, true, false));
        assertEquals(2, MenuInputTransfer.target(false, false, true));
        assertEquals(-1, MenuInputTransfer.target(false, false, false));
    }

    @Test void transferAmountsCoverSingleFullMergeIncompatibleAndFullInputs() {
        assertEquals(1, MenuInputTransfer.transferable(1, 0, 64, true, false));
        assertEquals(64, MenuInputTransfer.transferable(64, 0, 64, true, false));
        assertEquals(12, MenuInputTransfer.transferable(20, 52, 64, false, true));
        assertEquals(0, MenuInputTransfer.transferable(20, 52, 64, false, false));
        assertEquals(0, MenuInputTransfer.transferable(1, 64, 64, false, true));
        assertEquals(0, MenuInputTransfer.transferable(0, 0, 64, true, false));
    }
}
