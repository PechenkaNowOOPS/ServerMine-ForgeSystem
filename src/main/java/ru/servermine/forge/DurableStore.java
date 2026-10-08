package ru.servermine.forge;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
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
        if(failed) throw new IllegalStateException("Storage unavailable");
        String root="players."+p.getUniqueId();
        if(data.contains(root+".pending")) throw new IllegalStateException("Unrecovered operation");
        InventoryState state=new InventoryState(p,inputs(p.getUniqueId()));
        String rollback=data.saveToString();
        try { edit.accept(state); }
        catch(RuntimeException e) { try{data.loadFromString(rollback);}catch(Exception ignored){} throw e; }
        UUID operation=UUID.randomUUID();
        data.set(root+".inputs",encode(state.inputs));
        data.set(root+".pending.id",operation.toString()); data.set(root+".pending.contents",encode(state.contents)); data.set(root+".pending.cursor",encode(state.cursor));
        save();
        p.getInventory().setContents(state.contents); p.setItemOnCursor(state.cursor); p.saveData();
        data.set(root+".last-operation",operation.toString()); data.set(root+".pending",null); save();
    }
    void recover(Player p) {
        String root="players."+p.getUniqueId();
        if(data.contains(root+".pending")) {
            p.getInventory().setContents(decode(data.getStringList(root+".pending.contents"),41));
            p.setItemOnCursor(decode(data.getString(root+".pending.cursor"))); p.saveData();
            data.set(root+".pending",null); save();
        }
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
