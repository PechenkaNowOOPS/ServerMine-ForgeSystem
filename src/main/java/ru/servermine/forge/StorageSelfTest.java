package ru.servermine.forge;

import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;

/** Isolated transaction probes using real Paper item serialization; no real player or world is changed. */
final class StorageSelfTest {
    static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    static void run(Path parent) throws Exception {
        Path dir=Files.createTempDirectory(parent,"selftest-");Path file=dir.resolve("state.yml");
        UUID id=UUID.randomUUID();ItemStack[][] state={new ItemStack[41]};ItemStack[] cursor={null};int[] saves={0};
        var inventory=(PlayerInventory)Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),new Class<?>[]{PlayerInventory.class},(p,m,a)->switch(m.getName()){
            case "getContents" -> Arrays.stream(state[0]).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);
            case "setContents" -> {state[0]=(ItemStack[])a[0];yield null;}
            default -> throw new UnsupportedOperationException(m.getName());
        });
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "getInventory"->inventory;case "getItemOnCursor"->cursor[0]==null?new ItemStack(Material.AIR):cursor[0];
            case "setItemOnCursor"->{cursor[0]=(ItemStack)a[0];yield null;}case "saveData"->{saves[0]++;yield null;}
            default->throw new UnsupportedOperationException(m.getName());
        });
        try {
            DurableStore store=new DurableStore(file);state[0][0]=new ItemStack(Material.IRON_INGOT,10);
            store.transact(player,s->{s.inputs[1]=s.contents[0];s.contents[0]=null;});
            check(state[0][0]==null&&store.inputs(id)[1].getAmount()==10,"input transfer");
            check(saves[0]==1,"player data must be saved before journal cleared");
            String before=store.data.saveToString();
            DurableStore rollbackStore=store;
            try{store.transact(player,s->{rollbackStore.data.set("test",100);s.inputs[1]=null;throw new IllegalArgumentException("abort");});throw new IllegalStateException("missing rollback");}catch(IllegalArgumentException expected){}
            check(before.equals(store.data.saveToString())&&store.inputs(id)[1].getAmount()==10,"rollback changed escrow");
            store=new DurableStore(file);store.returnInputs(player);
            check(state[0][0].getAmount()==10&&store.inputs(id)[1]==null,"restart input return");
            store.returnInputs(player);check(state[0][0].getAmount()==10,"double close duplicate");
            for(int i=0;i<36;i++)state[0][i]=new ItemStack(Material.STONE,64);
            store.data.set("players."+id+".inputs",DurableStore.encode(new ItemStack[]{new ItemStack(Material.COAL,7),null,null}));store.save();store.returnInputs(player);
            check(store.data.getStringList("players."+id+".mail").size()==1&&store.inputs(id)[0]==null,"full inventory escrow");
            state[0][5]=null;store.claim(player);check(state[0][5].getType()==Material.COAL&&state[0][5].getAmount()==7,"mail claim");
            store.claim(player);check(state[0][5].getAmount()==7,"double claim duplicate");
            ItemStack[] after=new ItemStack[41];after[2]=new ItemStack(Material.GOLD_INGOT,5);
            String root="players."+id+".pending";store.data.set(root+".id",UUID.randomUUID().toString());store.data.set(root+".contents",DurableStore.encode(after));store.data.set(root+".cursor",DurableStore.encode(new ItemStack(Material.COAL,3)));store.save();
            DurableStore recovered=new DurableStore(file);recovered.recover(player);check(state[0][2].getAmount()==5&&cursor[0].getAmount()==3&&!recovered.data.contains(root),"write-ahead recovery");
            recovered.recover(player);check(state[0][2].getAmount()==5,"recovery duplicated items");
            ItemStack[] stable=Arrays.stream(state[0]).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);
            DurableStore failedCommit=new DurableStore(dir.resolve("missing-parent").resolve("state.yml"));
            boolean rejected=false;
            try {
                failedCommit.transact(player,s->{s.contents[0]=new ItemStack(Material.IRON_SWORD);},new DurableStore.WorldEffect(UUID.randomUUID(),1,2,3,"minecraft:water_cauldron[level=2]","minecraft:water_cauldron[level=1]"));
            } catch(IllegalStateException expected) {rejected=true;}
            check(rejected&&DurableStore.encode(stable).equals(DurableStore.encode(state[0]))&&failedCommit.failed,"failed journal write changed inventory before world effect");
        } finally {Files.deleteIfExists(file);Files.deleteIfExists(dir.resolve("state.yml.tmp"));Files.deleteIfExists(dir);}
    }
}
