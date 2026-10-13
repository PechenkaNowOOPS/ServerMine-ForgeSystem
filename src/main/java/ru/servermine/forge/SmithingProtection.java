package ru.servermine.forge;

import java.util.Arrays;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Prevents vanilla smithing from consuming ForgeSystem workpieces, including items with invalid signatures. */
final class SmithingProtection {
    private SmithingProtection() {}

    static boolean containsForgeWorkpiece(ItemStack[] ingredients) {
        return Arrays.stream(definitions(ingredients)).anyMatch("forgesystem:workpiece"::equals);
    }

    static boolean hasForgeIdentity(ItemStack item) {
        return ForgeItems.forgeWorkpieceIdentity(item);
    }

    static boolean mustBlock(ItemStack[] ingredients, ItemStack result) {
        return mustBlock(definitions(ingredients),ForgeItems.empty(result)?Material.AIR:result.getType());
    }

    static boolean mustBlock(String[] ingredientDefinitions, Material result) {
        return result!=null&&result!=Material.AIR&&Arrays.stream(ingredientDefinitions).anyMatch("forgesystem:workpiece"::equals);
    }

    private static String[] definitions(ItemStack[] items) {
        return Arrays.stream(items).map(i->hasForgeIdentity(i)?"forgesystem:workpiece":null).toArray(String[]::new);
    }
}
