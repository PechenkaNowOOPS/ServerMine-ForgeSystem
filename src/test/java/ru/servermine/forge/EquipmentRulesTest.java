package ru.servermine.forge;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import ru.servermine.forge.Model.Product;

class EquipmentRulesTest {
    @Test void blocksOnlyTheFourEquipmentTiers() {
        int count=0;for(Material m:Material.values())if(EquipmentRules.blockedCraft(m))count++;
        assertEquals(36,count);
        for(String tier:new String[]{"COPPER","IRON","GOLDEN","DIAMOND"})for(Product p:Product.values())
            assertTrue(EquipmentRules.blockedCraft(Material.valueOf(tier+"_"+p)));
        for(Material m:new Material[]{Material.WOODEN_PICKAXE,Material.STONE_SWORD,Material.LEATHER_CHESTPLATE,Material.NETHERITE_AXE,Material.IRON_BLOCK,Material.DIAMOND_BLOCK,Material.SHIELD,Material.SHEARS})
            assertFalse(EquipmentRules.blockedCraft(m),m.name());
    }
    @Test void upgradeUsesVanillaDiamondRecipeCosts() {
        assertArrayEquals(new int[]{2,3,3,1,2,5,8,7,4},java.util.Arrays.stream(Product.values()).mapToInt(EquipmentRules::diamonds).toArray());
    }
    @Test void wearIsPreservedWithoutFreeRepair() {
        assertEquals(0,EquipmentRules.upgradedDamage(0,250,1561));
        assertEquals(781,EquipmentRules.upgradedDamage(125,250,1561));
        assertEquals(1555,EquipmentRules.upgradedDamage(249,250,1561));
    }
}
