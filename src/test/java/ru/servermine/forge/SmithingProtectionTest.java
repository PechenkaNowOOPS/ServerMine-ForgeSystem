package ru.servermine.forge;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import ru.servermine.forge.Model.Product;

class SmithingProtectionTest {
    @Test void blocksAllNineSignedDiamondItemsAndKeepsOrdinaryVanillaSmithing() {
        for(Product product:Product.values()) {
            Material diamond=Material.valueOf("DIAMOND_"+product.name());
            assertTrue(SmithingProtection.mustBlock(new String[]{null,"forgesystem:workpiece",null},Material.valueOf("NETHERITE_"+product.name())),diamond.name());
        }
        assertFalse(SmithingProtection.mustBlock(new String[]{null,null,"minecraft:diamond"},Material.NETHERITE_SWORD));
    }

    @Test void malformedSignatureCannotBeLegitimizedThroughSmithing() {
        // The PDC identity is deliberately sufficient; signature validation cannot make a forged marker eligible.
        assertTrue(SmithingProtection.mustBlock(new String[]{null,"forgesystem:workpiece",null},Material.NETHERITE_PICKAXE));
        assertFalse(SmithingProtection.mustBlock(new String[]{null,"minecraft:diamond_pickaxe"},Material.NETHERITE_PICKAXE));
        assertFalse(SmithingProtection.mustBlock(new String[]{"forgesystem:workpiece"},Material.AIR));
    }
}
