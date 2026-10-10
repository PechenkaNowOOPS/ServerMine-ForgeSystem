package ru.servermine.forge;

import java.util.UUID;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import ru.servermine.forge.Model.*;

class QuenchRulesTest {
    private static Workpiece piece(State state,Product product) {
        return new Workpiece(UUID.randomUUID(),Metal.IRON,product,state,Quality.GOOD,1,1,1,UUID.randomUUID(),null,600,1,"test");
    }

    @Test void consumesExactlyOneCauldronLevelAndEmptiesAtOne() {
        assertEquals("minecraft:water_cauldron[level=2]",QuenchRules.nextCauldronData(Material.WATER_CAULDRON,3,3));
        assertEquals("minecraft:water_cauldron[level=1]",QuenchRules.nextCauldronData(Material.WATER_CAULDRON,2,3));
        assertEquals("minecraft:cauldron",QuenchRules.nextCauldronData(Material.WATER_CAULDRON,1,3));
    }

    @Test void rejectsWaterEmptyCauldronAndInvalidLevels() {
        assertThrows(IllegalArgumentException.class,()->QuenchRules.nextCauldronData(Material.WATER,3,3));
        assertThrows(IllegalArgumentException.class,()->QuenchRules.nextCauldronData(Material.CAULDRON,0,3));
        assertThrows(IllegalArgumentException.class,()->QuenchRules.nextCauldronData(Material.WATER_CAULDRON,0,3));
        assertThrows(IllegalArgumentException.class,()->QuenchRules.nextCauldronData(Material.WATER_CAULDRON,4,3));
    }

    @Test void permissionAndStateGateQuenchingAndPreventRepeats() {
        Workpiece unquenched=piece(State.UNQUENCHED,Product.SWORD);
        assertTrue(QuenchRules.canQuench(unquenched,true,Material.WATER_CAULDRON,3));
        assertFalse(QuenchRules.canQuench(unquenched,false,Material.WATER_CAULDRON,3));
        assertFalse(QuenchRules.canQuench(unquenched,true,Material.WATER,3));
        assertFalse(QuenchRules.canQuench(unquenched,true,Material.WATER_CAULDRON,0));
        assertFalse(QuenchRules.canQuench(piece(State.QUENCHED_PART,Product.SWORD),true,Material.WATER_CAULDRON,3));
        assertFalse(QuenchRules.canQuench(piece(State.FINISHED,Product.CHESTPLATE),true,Material.WATER_CAULDRON,3));
    }
}
