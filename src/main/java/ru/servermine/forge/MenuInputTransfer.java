package ru.servermine.forge;

import java.util.function.Predicate;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;

/** Deterministic routing and atomic stack movement for player-to-forge shift clicks. */
final class MenuInputTransfer {
    private MenuInputTransfer() {}

    static boolean isShiftClick(ClickType click) {
        return click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
    }

    /** Returns a player-storage slot only for a real move-to-menu action from the bottom inventory. */
    static int sourceSlot(boolean clickedPlayerInventory, int rawSlot, int localSlot, int convertedSlot,
                          int topSize, ClickType click, InventoryAction action) {
        if (!isShiftClick(click) || action != InventoryAction.MOVE_TO_OTHER_INVENTORY || !clickedPlayerInventory)
            return -1;
        if (topSize < 0 || rawSlot < topSize || rawSlot >= topSize + 36) return -1;
        if (localSlot < 0 || localSlot >= 36 || localSlot != convertedSlot) return -1;
        return localSlot;
    }

    static int target(boolean fuel, boolean ingot, boolean reheatableWorkpiece) {
        if (fuel) return 0;
        if (ingot) return 1;
        if (reheatableWorkpiece) return 2;
        return -1;
    }

    static int transferable(int sourceAmount, int targetAmount, int maxStackSize,
                            boolean targetEmpty, boolean similar) {
        if (sourceAmount < 1 || maxStackSize < 1) return 0;
        if (targetEmpty) return Math.min(sourceAmount, maxStackSize);
        if (!similar || targetAmount < 0 || targetAmount >= maxStackSize) return 0;
        return Math.min(sourceAmount, maxStackSize - targetAmount);
    }

    /** Mutates only after validation and capacity are known; false leaves both arrays untouched. */
    static boolean move(ItemStack[] contents, ItemStack[] inputs, int sourceSlot, int input,
                        Predicate<ItemStack> accepts) {
        if (sourceSlot < 0 || sourceSlot >= 36 || input < 0 || input >= inputs.length) return false;
        ItemStack source = contents[sourceSlot], target = inputs[input];
        if (ForgeItems.empty(source) || !accepts.test(source)) return false;
        boolean targetEmpty = ForgeItems.empty(target);
        boolean similar = !targetEmpty && target.isSimilar(source);
        int moved = transferable(source.getAmount(), targetEmpty ? 0 : target.getAmount(),
                source.getMaxStackSize(), targetEmpty, similar);
        if (moved == 0) return false;

        ItemStack nextTarget = targetEmpty ? source.clone() : target.clone();
        nextTarget.setAmount((targetEmpty ? 0 : target.getAmount()) + moved);
        ItemStack nextSource = source.clone();
        nextSource.setAmount(source.getAmount() - moved);
        inputs[input] = nextTarget;
        contents[sourceSlot] = nextSource.getAmount() == 0 ? null : nextSource;
        return true;
    }
}
