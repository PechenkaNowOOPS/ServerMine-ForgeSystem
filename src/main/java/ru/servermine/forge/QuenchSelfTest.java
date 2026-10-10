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
        UUID playerId=UUID.randomUUID();ItemStack[][] contents={new ItemStack[41]};boolean[] permitted={true};
        PlayerInventory inventory=(PlayerInventory)Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),new Class<?>[]{PlayerInventory.class},(proxy,method,args)->switch(method.getName()){
            case "getContents"->Arrays.stream(contents[0]).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);
            case "setContents"->{contents[0]=(ItemStack[])args[0];yield null;}
            case "getHeldItemSlot"->0;case "getItemInMainHand"->contents[0][0];
            default->throw new UnsupportedOperationException(method.getName());
        });
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()){
            case "getUniqueId"->playerId;case "getInventory"->inventory;case "getItemOnCursor"->new ItemStack(Material.AIR);
            case "setItemOnCursor","saveData","playSound"->null;case "hasPermission"->permitted[0];case "getLocation"->block.getLocation();
            default->throw new UnsupportedOperationException(method.getName());
        });
        try {
            plugin.store=new DurableStore(dir.resolve("state.yml"));
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
            try{plugin.forging.quenchOrAssemble(player,block);throw new IllegalStateException("Failed journal write was accepted");}
            catch(IllegalStateException expected){}
            check(contents[0][0].equals(failed)&&block.getBlockData().getAsString().endsWith("level=3]"),"Failed journal write changed item or water");
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
    private static ItemStack unquenched(ForgeSystemPlugin plugin,Product product) {
        ItemStack item=plugin.items.createWorkpiece(Metal.IRON,product,900);Workpiece blank=plugin.items.readWorkpiece(item).orElseThrow();UUID smith=UUID.randomUUID();long now=System.currentTimeMillis();
        Workpiece forging=blank.progress(State.FORGING,Quality.NONE,1,3,smith,UUID.randomUUID(),900,now);item=plugin.items.change(item,forging);
        return plugin.items.change(item,forging.progress(State.UNQUENCHED,Quality.GOOD,1,1,smith,null,900,now));
    }
}
