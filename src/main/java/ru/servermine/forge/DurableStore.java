package ru.servermine.forge;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Write-ahead player snapshots: journal -> apply -> save player -> clear journal.
 * Inputs belong to persistent per-player escrow; they are never dropped into the world. */
final class DurableStore {
    final Path path;
    YamlConfiguration data=new YamlConfiguration();
    boolean failed;
    DurableStore(Path path) throws Exception { this.path=path; if(Files.exists(path)) data.load(path.toFile()); }
    static String encode(ItemStack i) { return ForgeItems.empty(i)?"":Base64.getEncoder().encodeToString(i.serializeAsBytes()); }
    static ItemStack decode(String s) { return s==null||s.isBlank()?null:ItemStack.deserializeBytes(Base64.getDecoder().decode(s)); }
    static List<String> encode(ItemStack[] items) { return Arrays.stream(items).map(DurableStore::encode).toList(); }
    static ItemStack[] decode(List<String> items,int count) { ItemStack[] a=new ItemStack[count]; for(int n=0;n<Math.min(count,items.size());n++) a[n]=decode(items.get(n)); return a; }
    record WorldEffect(UUID world,int x,int y,int z,String before,String after) {
        static WorldEffect capture(Block block,String after) {
            return new WorldEffect(block.getWorld().getUID(),block.getX(),block.getY(),block.getZ(),block.getBlockData().getAsString(),after);
        }
        void apply() {
            World target=Bukkit.getWorld(world);
            if(target==null)throw new IllegalStateException("World for pending ForgeSystem block operation is unavailable: "+world);
            Block block=target.getBlockAt(x,y,z);String current=block.getBlockData().getAsString();
            if(current.equals(after)){target.save(true);return;}
            if(!current.equals(before))throw new IllegalStateException("Pending ForgeSystem block operation conflicts with changed world at "+world+":"+x+","+y+","+z);
            BlockData data=Bukkit.createBlockData(after);block.setBlockData(data,false);target.save(true);
            if(!block.getBlockData().getAsString().equals(after))throw new IllegalStateException("Could not persist ForgeSystem block operation");
        }
        WorldEffect reverse() {return new WorldEffect(world,x,y,z,after,before);}
    }
    void save() {
        if(failed) throw new IllegalStateException("Storage is in fail-closed mode");
        try {
            byte[] bytes=data.saveToString().getBytes(StandardCharsets.UTF_8); Path temp=path.resolveSibling(path.getFileName()+".tmp");
            try(var channel=FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) { var buffer=ByteBuffer.wrap(bytes); while(buffer.hasRemaining()) channel.write(buffer); channel.force(true); }
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch(Exception e) { failed=true; throw new IllegalStateException("Cannot commit forge state; operations stopped",e); }
    }
    static final class InventoryState {
        ItemStack[] contents,inputs; ItemStack cursor;
        InventoryState(Player p,ItemStack[] inputs) { contents=Arrays.stream(p.getInventory().getContents()).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new); this.inputs=Arrays.stream(inputs).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new); cursor=p.getItemOnCursor().clone(); }
        boolean add(ItemStack item) {
            if(ForgeItems.empty(item)) return true; int remaining=item.getAmount();
            for(int n=0;n<36;n++) { var old=contents[n]; if(!ForgeItems.empty(old)&&old.isSimilar(item)) { int take=Math.min(remaining,old.getMaxStackSize()-old.getAmount()); old.setAmount(old.getAmount()+take); remaining-=take; if(remaining==0) return true; } }
            for(int n=0;n<36;n++) if(ForgeItems.empty(contents[n])) { int take=Math.min(remaining,item.getMaxStackSize()); contents[n]=item.clone(); contents[n].setAmount(take); remaining-=take; if(remaining==0) return true; }
            return false;
        }
    }
    ItemStack[] inputs(UUID id) { return decode(data.getStringList("players."+id+".inputs"),3); }
    void transact(Player p,Consumer<InventoryState> edit) {
        transact(p,edit,null);
    }
    void transact(Player p,Consumer<InventoryState> edit,WorldEffect effect) {
        if(failed) throw new IllegalStateException("Storage unavailable");
        String root="players."+p.getUniqueId();
        if(data.contains(root+".pending")) throw new IllegalStateException("Unrecovered operation");
        InventoryState state=new InventoryState(p,inputs(p.getUniqueId()));
        ItemStack[] previousContents=Arrays.stream(state.contents).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);
        ItemStack previousCursor=state.cursor==null?null:state.cursor.clone();
        String rollback=data.saveToString();
        try { edit.accept(state); }
        catch(RuntimeException e) { try{data.loadFromString(rollback);}catch(Exception ignored){} throw e; }
        UUID operation=UUID.randomUUID();
        data.set(root+".inputs",encode(state.inputs));
        data.set(root+".pending.id",operation.toString()); data.set(root+".pending.contents",encode(state.contents)); data.set(root+".pending.cursor",encode(state.cursor));
        WorldEffect worldEffect=effect;writeWorldEffect(root+".pending.world-effect",worldEffect);
        try {save();} catch(RuntimeException ex) {try{data.loadFromString(rollback);}catch(Exception ignored){}throw ex;}
        String journalSnapshot=data.saveToString();
        try {
            if(worldEffect!=null)worldEffect.apply();
            p.getInventory().setContents(state.contents); p.setItemOnCursor(state.cursor); p.saveData();
            data.set(root+".last-operation",operation.toString()); data.set(root+".pending",null); save();
        } catch(RuntimeException ex) {
            if(failed) {
                try{data.loadFromString(journalSnapshot);}catch(Exception ignored){}
                throw new IllegalStateException("ForgeSystem operation remains journaled and requires recovery",ex);
            }
            try {
                p.getInventory().setContents(previousContents);p.setItemOnCursor(previousCursor);p.saveData();
                if(worldEffect!=null)worldEffect.reverse().apply();
                data.loadFromString(rollback);save();
            } catch(Exception compensation) {
                try{data.loadFromString(journalSnapshot);}catch(Exception ignored){}
                failed=true;throw new IllegalStateException("ForgeSystem operation remains journaled after compensation failed",compensation);
            }
            throw new IllegalStateException("ForgeSystem operation was rolled back",ex);
        }
    }
    void recover(Player p) {
        String root="players."+p.getUniqueId();
        if(data.contains(root+".pending")) {
            String pendingSnapshot=data.saveToString();
            try {
                p.getInventory().setContents(decode(data.getStringList(root+".pending.contents"),41));
                p.setItemOnCursor(decode(data.getString(root+".pending.cursor"))); p.saveData();
                WorldEffect effect=readWorldEffect(root+".pending.world-effect");if(effect!=null)effect.apply();
                data.set(root+".last-operation",data.getString(root+".pending.id"));data.set(root+".pending",null);save();failed=false;
            } catch(RuntimeException ex) {try{data.loadFromString(pendingSnapshot);}catch(Exception ignored){}failed=true;throw new IllegalStateException("Pending ForgeSystem operation could not be recovered",ex); }
        }
    }
    void writeWorldEffect(String root,WorldEffect effect) {
        if(effect==null){data.set(root,null);return;}
        data.set(root+".world",effect.world().toString());data.set(root+".x",effect.x());data.set(root+".y",effect.y());data.set(root+".z",effect.z());
        data.set(root+".before",effect.before());data.set(root+".after",effect.after());
    }
    WorldEffect readWorldEffect(String root) {
        if(!data.contains(root+".world"))return null;
        return new WorldEffect(UUID.fromString(data.getString(root+".world")),data.getInt(root+".x"),data.getInt(root+".y"),data.getInt(root+".z"),
                Objects.requireNonNull(data.getString(root+".before")),Objects.requireNonNull(data.getString(root+".after")));
    }
    void returnInputs(Player p) {
        transact(p,s->{for(int i=0;i<3;i++) { giveOrMail(p,s,s.inputs[i]); s.inputs[i]=null; }});
    }
    void giveOrMail(Player p,InventoryState s,ItemStack item) {
        if(ForgeItems.empty(item)) return;
        ItemStack[] before=Arrays.stream(s.contents).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);
        if(!s.add(item)) { s.contents=before; String k="players."+p.getUniqueId()+".mail"; var mail=new ArrayList<>(data.getStringList(k)); mail.add(encode(item)); data.set(k,mail); }
    }
    void claim(Player p) {
        transact(p,s->{String k="players."+p.getUniqueId()+".mail"; var old=data.getStringList(k); data.set(k,null); for(String item:old) giveOrMail(p,s,decode(item));});
    }
}
