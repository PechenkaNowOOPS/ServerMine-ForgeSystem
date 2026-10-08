package ru.servermine.forge;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.servermine.forge.Model.*;
import static org.junit.jupiter.api.Assertions.*;

class ItemSpritesTest {
    private Workpiece blank(Metal metal,Product product){return new Workpiece(UUID.randomUUID(),metal,product,State.HOT_BLANK,Quality.NONE,0,1,3,null,null,1000,1000,"test");}

    @Test void threeHeatVariantsSwitchAtExactBoundaries(){
        var w=blank(Metal.IRON,Product.SWORD);
        assertEquals("forge/workpiece/iron/sword/cold",ItemSprites.workpiece(w,299.9,700));
        assertEquals("forge/workpiece/iron/sword/warm",ItemSprites.workpiece(w,300,700));
        assertEquals("forge/workpiece/iron/sword/warm",ItemSprites.workpiece(w,699.9,700));
        assertEquals("forge/workpiece/iron/sword/hot",ItemSprites.workpiece(w,700,700));
        assertEquals(State.HOT_BLANK,w.state());
    }
    @Test void goldUsesItsOwnWorkingTemperature(){
        var w=blank(Metal.GOLD,Product.CHESTPLATE);
        assertTrue(ItemSprites.workpiece(w,599.9,600).endsWith("/warm"));
        assertTrue(ItemSprites.workpiece(w,600,600).endsWith("/hot"));
    }
    @Test void copperCoolsThroughAllThreeVariantsAndReheats(){
        var w=blank(Metal.COPPER,Product.PICKAXE).thermal(900,1000);
        assertEquals("forge/workpiece/copper/pickaxe/hot",ItemSprites.workpiece(w,w.at(1000,20,28),550));
        assertEquals("forge/workpiece/copper/pickaxe/warm",ItemSprites.workpiece(w,w.at(14000,20,28),550));
        assertEquals("forge/workpiece/copper/pickaxe/cold",ItemSprites.workpiece(w,w.at(24000,20,28),550));
        assertTrue(ItemSprites.workpiece(w.thermal(900,25000),900,550).endsWith("/hot"));
    }
    @Test void coolingAndReheatingDoNotChangeProgress(){
        var w=blank(Metal.IRON,Product.AXE).progress(State.FORGING,Quality.GOOD,2,2,UUID.randomUUID(),null,1000,1000);
        var before=w;
        assertTrue(ItemSprites.workpiece(w,w.at(1000,20,22),700).endsWith("/hot"));
        assertTrue(ItemSprites.workpiece(w,w.at(16000,20,22),700).endsWith("/warm"));
        assertTrue(ItemSprites.workpiece(w,w.at(40000,20,22),700).endsWith("/cold"));
        assertTrue(ItemSprites.workpiece(w.thermal(1000,41000),1000,700).endsWith("/hot"));
        assertEquals(before,w);
    }
    @Test void allFinishedProductsUseVanillaAppearance(){
        for(Metal metal:Metal.values())for(Product product:Product.values()){
            var finished=blank(metal,product).progress(State.FINISHED,Quality.GOOD,1,1,UUID.randomUUID(),null,20,1000);
            assertNull(ItemSprites.workpiece(finished,20,700));
        }
    }
}
