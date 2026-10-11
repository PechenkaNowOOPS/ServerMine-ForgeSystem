package ru.servermine.forge;

import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import ru.servermine.forge.Model.*;

/** Runs only from forge selftest on an isolated test server; restores its temporary air block. */
final class QuenchSelfTest {
    private QuenchSelfTest() {}
    static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}

    static void run(ForgeSystemPlugin plugin) throws Exception {
        Path dir=Files.createTempDirectory(plugin.getDataFolder().toPath(),"quench-selftest-");
        DurableStore previousStore=plugin.store;World world=Bukkit.getWorlds().get(0);Block block=findAir(world);String before=block.getBlockData().getAsString();
        UUID playerId=UUID.randomUUID();ItemStack[][] contents={new ItemStack[41]};ItemStack[] cursor={new ItemStack(Material.AIR)};int[] inventoryWrites={0},cursorWrites={0},playerSaves={0};boolean[] permitted={true};
        PlayerInventory inventory=(PlayerInventory)Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),new Class<?>[]{PlayerInventory.class},(proxy,method,args)->switch(method.getName()){
            case "getContents"->Arrays.stream(contents[0]).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);
            case "setContents"->{contents[0]=(ItemStack[])args[0];inventoryWrites[0]++;yield null;}
            case "getHeldItemSlot"->0;case "getItemInMainHand"->contents[0][0];
            default->throw new UnsupportedOperationException(method.getName());
        });
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()){
            case "getUniqueId"->playerId;case "getInventory"->inventory;case "getItemOnCursor"->cursor[0];
            case "setItemOnCursor"->{cursor[0]=(ItemStack)args[0];cursorWrites[0]++;yield null;}
            case "saveData"->{playerSaves[0]++;yield null;}case "playSound"->null;case "hasPermission"->permitted[0];case "getLocation"->block.getLocation();
            default->throw new UnsupportedOperationException(method.getName());
        });
        try {
            plugin.store=new DurableStore(dir.resolve("state.yml"));
            setCauldron(block,1);DurableStore.WorldEffect rollbackProbe=DurableStore.WorldEffect.capture(block,"minecraft:cauldron");
            rollbackProbe.apply();check(block.getType()==Material.CAULDRON,"Level-one quench transition failed");rollbackProbe.reverse().applyRollback();
            check(block.getBlockData().getAsString().endsWith("level=1]"),"Level-one world rollback failed");
            for(int level=3;level>=1;level--) {
                setCauldron(block,level);contents[0][0]=unquenched(plugin,Product.SWORD);
                check(plugin.forging.quenchOrAssemble(player,block),"Filled cauldron did not quench at level "+level);
                Workpiece result=plugin.items.readWorkpiece(contents[0][0]).orElseThrow();
                check(result.state()==State.QUENCHED_PART,"Sword transition was incorrect");
                String expected=QuenchRules.nextCauldronData(Material.WATER_CAULDRON,level,3);
                check(block.getBlockData().getAsString().equals(expected),"Cauldron level was not consumed exactly once at "+level);
                ItemStack after=contents[0][0].clone();check(!plugin.forging.quenchOrAssemble(player,block),"Repeated quench was accepted");
                check(contents[0][0].equals(after)&&block.getBlockData().getAsString().equals(expected),"Repeated quench changed item or water");
            }
            setCauldron(block,1);contents[0][0]=unquenched(plugin,Product.CHESTPLATE);
            check(plugin.forging.quenchOrAssemble(player,block),"Armor quench failed");
            check(plugin.items.readWorkpiece(contents[0][0]).orElseThrow().state()==State.FINISHED&&block.getType()==Material.CAULDRON,"Armor or empty-cauldron transition failed");

            block.setType(Material.WATER,false);contents[0][0]=unquenched(plugin,Product.PICKAXE);ItemStack waterInput=contents[0][0].clone();
            check(!plugin.forging.quenchOrAssemble(player,block)&&contents[0][0].equals(waterInput)&&block.getType()==Material.WATER,"Ordinary water changed quench state");

            setCauldron(block,3);contents[0][0]=unquenched(plugin,Product.AXE);ItemStack denied=contents[0][0].clone();permitted[0]=false;
            try{plugin.forging.quenchOrAssemble(player,block);throw new IllegalStateException("Permission-denied quench succeeded");}
            catch(IllegalArgumentException expected){}
            check(contents[0][0].equals(denied)&&block.getBlockData().getAsString().endsWith("level=3]"),"Permission failure changed item or water");permitted[0]=true;

            plugin.store=new DurableStore(dir.resolve("missing-parent").resolve("state.yml"));contents[0][0]=unquenched(plugin,Product.SHOVEL);ItemStack failed=contents[0][0].clone();
            boolean journalFailureRejected=false;
            try{plugin.forging.quenchOrAssemble(player,block);}
            catch(IllegalStateException expected){journalFailureRejected=expected.getMessage()!=null&&expected.getMessage().contains("Cannot commit forge state");}
            check(journalFailureRejected,"Failed journal write was accepted or reported incorrectly");
            check(contents[0][0].equals(failed)&&block.getBlockData().getAsString().endsWith("level=3]"),"Failed journal write changed item or water");

            String root="players."+playerId;ItemStack conflictInput=unquenched(plugin,Product.SWORD);contents[0][0]=conflictInput;
            Workpiece conflictWorkpiece=plugin.items.readWorkpiece(conflictInput).orElseThrow();
            ItemStack conflictOutput=plugin.items.change(conflictInput,conflictWorkpiece.progress(State.QUENCHED_PART,conflictWorkpiece.quality(),conflictWorkpiece.stage(),conflictWorkpiece.cap(),conflictWorkpiece.smith(),null,20,System.currentTimeMillis()));
            ItemStack[] conflictContents=contents[0].clone();conflictContents[0]=conflictOutput;setCauldron(block,3);
            DurableStore.WorldEffect conflictEffect=DurableStore.WorldEffect.capture(block,"minecraft:water_cauldron[level=2]");block.setType(Material.STONE,false);
            DurableStore conflictStore=pending(dir.resolve("recovery-conflict.yml"),root,conflictContents,cursor[0],conflictEffect);
            plugin.store=conflictStore;int writesBefore=inventoryWrites[0],cursorBefore=cursorWrites[0],savesBefore=playerSaves[0];
            boolean conflictRejected=false;try{plugin.store.recover(player);}catch(IllegalStateException expected){conflictRejected=expected.getMessage()!=null&&expected.getMessage().contains("could not be recovered");}
            check(conflictRejected&&inventoryWrites[0]==writesBefore&&cursorWrites[0]==cursorBefore&&playerSaves[0]==savesBefore,"Conflicting block changed player inventory or playerdata");
            check(conflictStore.data.contains(root+".pending")&&block.getType()==Material.STONE,"Conflict cleared journal or changed block");

            setCauldron(block,3);ItemStack recoveryInput=unquenched(plugin,Product.PICKAXE);Workpiece recoveryWorkpiece=plugin.items.readWorkpiece(recoveryInput).orElseThrow();
            ItemStack recoveryOutput=plugin.items.change(recoveryInput,recoveryWorkpiece.progress(State.QUENCHED_PART,recoveryWorkpiece.quality(),recoveryWorkpiece.stage(),recoveryWorkpiece.cap(),recoveryWorkpiece.smith(),null,20,System.currentTimeMillis()));
            ItemStack[] recoveryContents=new ItemStack[41];recoveryContents[0]=recoveryOutput;
            DurableStore recoveryStore=pending(dir.resolve("recovery-success.yml"),root,recoveryContents,cursor[0],DurableStore.WorldEffect.capture(block,"minecraft:water_cauldron[level=2]"));
            plugin.store=recoveryStore;plugin.store.recover(player);
            check(contents[0][0].equals(recoveryOutput)&&block.getBlockData().getAsString().endsWith("level=2]"),"Recovery failed to apply block before expected item");
            int completedWrites=inventoryWrites[0],completedSaves=playerSaves[0];contents[0][0]=new ItemStack(Material.DIAMOND);
            plugin.store=new DurableStore(dir.resolve("recovery-success.yml"));plugin.store.recover(player);
            check(contents[0][0].getType()==Material.DIAMOND&&inventoryWrites[0]==completedWrites&&playerSaves[0]==completedSaves&&block.getBlockData().getAsString().endsWith("level=2]"),"Completed recovery replay changed player or water");

            setCauldron(block,2);pending(dir.resolve("recovery-after.yml"),root,recoveryContents,cursor[0],DurableStore.WorldEffect.capture(block,"minecraft:water_cauldron[level=1]"));
            setCauldron(block,1);plugin.store=new DurableStore(dir.resolve("recovery-after.yml"));plugin.store.recover(player);
            check(block.getBlockData().getAsString().endsWith("level=1]")&&contents[0][0].equals(recoveryOutput),"Recovery from already-applied block consumed water twice or missed inventory");
        } finally {
            plugin.store=previousStore;block.setBlockData(Bukkit.createBlockData(before),false);world.save();
            try(var paths=Files.walk(dir)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
        }
    }

    private static Block findAir(World world) {
        Location spawn=world.getSpawnLocation();int min=world.getMinHeight(),max=world.getMaxHeight();
        for(int y=Math.max(min,spawn.getBlockY()+8);y<max-1;y++){Block block=world.getBlockAt(spawn.getBlockX(),y,spawn.getBlockZ());if(block.getType()==Material.AIR)return block;}
        throw new IllegalStateException("No temporary air block found for quench selftest");
    }
    private static void setCauldron(Block block,int level){block.setBlockData(Bukkit.createBlockData("minecraft:water_cauldron[level="+level+"]"),false);}
    private static DurableStore pending(Path path,String root,ItemStack[] contents,ItemStack cursor,DurableStore.WorldEffect effect) throws Exception {
        DurableStore store=new DurableStore(path);store.data.set(root+".pending.id",UUID.randomUUID().toString());
        store.data.set(root+".pending.contents",DurableStore.encode(contents));store.data.set(root+".pending.cursor",DurableStore.encode(cursor));
        store.writeWorldEffect(root+".pending.world-effect",effect);store.save();return store;
    }
    private static ItemStack unquenched(ForgeSystemPlugin plugin,Product product) {
        ItemStack item=plugin.items.createWorkpiece(Metal.IRON,product,900);Workpiece blank=plugin.items.readWorkpiece(item).orElseThrow();UUID smith=UUID.randomUUID();long now=System.currentTimeMillis();
        Workpiece forging=blank.progress(State.FORGING,Quality.NONE,1,3,smith,UUID.randomUUID(),900,now);item=plugin.items.change(item,forging);
        return plugin.items.change(item,forging.progress(State.UNQUENCHED,Quality.GOOD,1,1,smith,null,900,now));
    }
}
