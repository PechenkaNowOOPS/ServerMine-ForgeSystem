package ru.servermine.forge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import static org.junit.jupiter.api.Assertions.*;

class DurableStoreWorldEffectTest {
    private static final String BEFORE="minecraft:water_cauldron[level=3]";
    private static final String AFTER="minecraft:water_cauldron[level=2]";
    private static final UUID PLAYER_ID=UUID.fromString("1b719216-7ba5-4a43-8303-ecedb6dde324");

    @Test void pendingWorldEffectSurvivesReloadWithExactTargetAndOrigin() throws Exception {
        var dir=Files.createTempDirectory("forge-world-effect-");var path=dir.resolve("state.yml");UUID world=UUID.randomUUID();
        try {
            var effect=new DurableStore.WorldEffect(world,17,64,-23,"minecraft:water_cauldron[level=3]","minecraft:water_cauldron[level=2]");
            var store=new DurableStore(path);store.writeWorldEffect("players.test.pending.world-effect",effect);store.save();
            var recovered=new DurableStore(path).readWorldEffect("players.test.pending.world-effect");
            assertEquals(effect,recovered);
        } finally {Files.deleteIfExists(path);Files.deleteIfExists(path.resolveSibling("state.yml.tmp"));Files.deleteIfExists(dir);}
    }

    @Test void recoveryAppliesWorldBeforeInventoryAndCompletesJournal() throws Exception {
        var f=new Fixture(Files.createTempDirectory("forge-recover-ok-"));String[] block={BEFORE};int[] writes={0};
        var effect=f.effect();DurableStore store=f.pending(effect);
        store=new DurableStore(f.path,(pending)->{assertEquals(effect,pending);f.order.add("world");if(block[0].equals(BEFORE)){block[0]=AFTER;writes[0]++;}else assertEquals(AFTER,block[0]);});
        f.journal(store,effect);
        store.recover(f.player);
        assertEquals(AFTER,block[0]);assertEquals(1,writes[0]);assertEquals(1,f.inventoryWrites[0]);
        assertEquals(1,f.playerSaves[0]);assertFalse(store.data.contains(f.root+".pending"));
        assertEquals(List.of("world","inventory","cursor","playerdata"),f.order);
    }

    @Test void conflictLeavesPlayerUntouchedAndJournalPending() throws Exception {
        var f=new Fixture(Files.createTempDirectory("forge-recover-conflict-"));String[] block={"minecraft:lava"};
        var effect=f.effect();DurableStore store=f.pending(effect);
        store=new DurableStore(f.path,pending->{if(!block[0].equals(BEFORE)&&!block[0].equals(AFTER))throw new IllegalStateException("block conflict");});
        f.journal(store,effect);DurableStore recoveryStore=store;
        assertThrows(IllegalStateException.class,()->recoveryStore.recover(f.player));
        assertEquals(0,f.inventoryWrites[0]);assertEquals(0,f.cursorWrites[0]);assertEquals(0,f.playerSaves[0]);
        assertTrue(store.failed);assertTrue(store.data.contains(f.root+".pending"));assertEquals("minecraft:lava",block[0]);
    }

    @Test void worldSaveFailureLeavesInventoryUntouchedAndRestartCanFinishAfterBlockWasWritten() throws Exception {
        var f=new Fixture(Files.createTempDirectory("forge-recover-save-failure-"));String[] block={BEFORE};
        var effect=f.effect();DurableStore store=f.pending(effect);
        store=new DurableStore(f.path,pending->{block[0]=AFTER;throw new IllegalStateException("world save failed");});f.journal(store,effect);DurableStore recoveryStore=store;
        assertThrows(IllegalStateException.class,()->recoveryStore.recover(f.player));
        assertEquals(0,f.inventoryWrites[0]);assertEquals(0,f.cursorWrites[0]);assertEquals(0,f.playerSaves[0]);assertTrue(store.data.contains(f.root+".pending"));

        DurableStore restarted=new DurableStore(f.path,pending->{assertEquals(AFTER,block[0]);});
        f.order.clear();restarted.recover(f.player);
        assertEquals(1,f.inventoryWrites[0]);assertFalse(restarted.data.contains(f.root+".pending"));
    }

    @Test void completedRecoveryIsIdempotentAcrossRestartAndDoesNotOverwriteLaterInventory() throws Exception {
        var f=new Fixture(Files.createTempDirectory("forge-recover-restart-"));String[] block={AFTER};var effect=f.effect();
        DurableStore first=f.pending(effect);first=new DurableStore(f.path,pending->{assertEquals(AFTER,block[0]);});f.journal(first,effect);
        first.recover(f.player);assertEquals(1,f.playerSaves[0]);
        f.inventoryWrites[0]++;
        DurableStore restarted=new DurableStore(f.path,pending->{fail("completed operation replayed world effect");});
        restarted.recover(f.player);
        assertEquals(2,f.inventoryWrites[0]);assertEquals(1,f.playerSaves[0]);
    }

    @Test void legacyPendingWithoutWorldEffectStillRecovers() throws Exception {
        var f=new Fixture(Files.createTempDirectory("forge-recover-legacy-"));DurableStore store=f.pending(null);
        store=new DurableStore(f.path,pending->fail("unexpected world effect"));f.journal(store,null);
        store.recover(f.player);
        assertEquals(1,f.inventoryWrites[0]);assertEquals(1,f.playerSaves[0]);assertFalse(store.data.contains(f.root+".pending"));
        assertEquals(List.of("inventory","cursor","playerdata"),f.order);
    }

    @Test void malformedPendingSnapshotIsRejectedBeforeAnyPlayerMutation() throws Exception {
        var f=new Fixture(Files.createTempDirectory("forge-recover-malformed-"));DurableStore store=f.pending(null);
        store.data.set(f.root+".pending.cursor","%%%not-valid-base64%%%");store.save();
        DurableStore recoveryStore=new DurableStore(f.path,pending->fail("unexpected world effect"));
        assertThrows(IllegalStateException.class,()->recoveryStore.recover(f.player));
        assertEquals(0,f.inventoryWrites[0]);assertEquals(0,f.cursorWrites[0]);assertEquals(0,f.playerSaves[0]);
        assertTrue(recoveryStore.failed);assertTrue(recoveryStore.data.contains(f.root+".pending"));
    }

    private static final class Fixture {
        final Path path;final String root="players."+PLAYER_ID;final ItemStack[][] contents={new ItemStack[41]};
        final ItemStack[] cursor={null};final int[] inventoryWrites={0},cursorWrites={0},playerSaves={0};
        final java.util.List<String> order=new java.util.ArrayList<>();final Player player;
        Fixture(Path dir) throws Exception {
            path=dir.resolve("state.yml");
            PlayerInventory inventory=(PlayerInventory)Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),new Class<?>[]{PlayerInventory.class},(proxy,method,args)->switch(method.getName()){
                case "getContents"->Arrays.stream(contents[0]).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);
                case "setContents"->{contents[0]=(ItemStack[])args[0];inventoryWrites[0]++;order.add("inventory");yield null;}
                default->throw new UnsupportedOperationException(method.getName());
            });
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()){
                case "getUniqueId"->PLAYER_ID;case "getInventory"->inventory;
                case "getItemOnCursor"->cursor[0];case "setItemOnCursor"->{cursor[0]=(ItemStack)args[0];cursorWrites[0]++;order.add("cursor");yield null;}
                case "saveData"->{playerSaves[0]++;order.add("playerdata");yield null;}
                default->throw new UnsupportedOperationException(method.getName());
            });
        }
        DurableStore.WorldEffect effect(){return new DurableStore.WorldEffect(UUID.randomUUID(),4,70,9,BEFORE,AFTER);}
        DurableStore pending(DurableStore.WorldEffect effect) throws Exception {DurableStore store=new DurableStore(path,pending->{order.add("world");});journal(store,effect);return store;}
        void journal(DurableStore store,DurableStore.WorldEffect effect){
            ItemStack[] desired=new ItemStack[41];
            store.data.set(root+".pending.id",UUID.randomUUID().toString());store.data.set(root+".pending.contents",DurableStore.encode(desired));
            store.data.set(root+".pending.cursor",DurableStore.encode((ItemStack)null));
            store.writeWorldEffect(root+".pending.world-effect",effect);store.save();
        }
    }
}
