package ru.servermine.forge;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.InventoryView;
import ru.servermine.forge.Model.*;

/** Exercises the production shift-transfer transaction with server-backed ItemStacks and isolated state. */
final class MenuInputTransferSelfTest {
    private MenuInputTransferSelfTest() {}

    static void run(ForgeSystemPlugin plugin) throws Exception {
        Path dir=Files.createTempDirectory(plugin.getDataFolder().toPath(),"menu-transfer-selftest-");
        DurableStore original=plugin.store;
        ItemStack[][] contents={new ItemStack[41]};ItemStack[] cursor={new ItemStack(Material.STONE,3)};int[] saves={0};
        UUID id=UUID.randomUUID();
        PlayerInventory inventory=(PlayerInventory)Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),new Class<?>[]{PlayerInventory.class},(proxy,method,args)->switch(method.getName()){
            case "getContents"->Arrays.stream(contents[0]).map(item->item==null?null:item.clone()).toArray(ItemStack[]::new);
            case "setContents"->{contents[0]=Arrays.stream((ItemStack[])args[0]).map(item->item==null?null:item.clone()).toArray(ItemStack[]::new);yield null;}
            default->throw new IllegalStateException("Unexpected inventory call: "+method.getName());
        });
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()){
            case "getUniqueId"->id;case "getInventory"->inventory;case "getItemOnCursor"->cursor[0].clone();
            case "setItemOnCursor"->{cursor[0]=((ItemStack)args[0]).clone();yield null;}case "saveData"->{saves[0]++;yield null;}
            case "sendMessage"->null;
            default->throw new IllegalStateException("Unexpected player call: "+method.getName());
        });
        try {
            plugin.store=new DurableStore(dir.resolve("state.yml"));
            checkShift(plugin,player,contents,0,new ItemStack(Material.COAL),0,1);
            setInput(plugin,player,0,null);
            checkShift(plugin,player,contents,0,new ItemStack(Material.COAL,64),0,64);
            setInput(plugin,player,0,null);
            checkShift(plugin,player,contents,35,new ItemStack(Material.IRON_INGOT,1),1,1);
            setInput(plugin,player,1,null);
            checkShift(plugin,player,contents,2,new ItemStack(Material.GOLD_INGOT,64),1,64);
            setInput(plugin,player,1,null);
            checkShift(plugin,player,contents,3,new ItemStack(Material.COPPER_INGOT,64),1,64);
            setInput(plugin,player,1,null);

            ItemStack blank=plugin.items.createWorkpiece(Metal.IRON,Product.PICKAXE,1000);
            checkShift(plugin,player,contents,4,blank,2,1);
            setInput(plugin,player,2,null);
            Workpiece workpiece=plugin.items.readWorkpiece(blank).orElseThrow();
            ItemStack locked=plugin.items.change(blank,workpiece.progress(State.FORGING,Quality.NONE,1,3,UUID.randomUUID(),UUID.randomUUID(),1000,System.currentTimeMillis()));
            rejectUnchanged(plugin,player,contents,5,locked,"locked workpiece");
            rejectUnchanged(plugin,player,contents,6,new ItemStack(Material.DIAMOND),"unsupported item");

            setInput(plugin,player,0,new ItemStack(Material.CHARCOAL,4));
            rejectUnchanged(plugin,player,contents,7,new ItemStack(Material.COAL,3),"incompatible occupied input");
            setInput(plugin,player,0,new ItemStack(Material.COAL,62));
            contents[0][8]=new ItemStack(Material.COAL,5);click(plugin,player,8,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY,true);
            check(plugin.store.inputs(id)[0].getAmount()==64&&contents[0][8].getAmount()==3,"partial merge source remainder");
            setInput(plugin,player,0,new ItemStack(Material.COAL,64));
            rejectUnchanged(plugin,player,contents,9,new ItemStack(Material.COAL),"full input");
            contents[0][10]=new ItemStack(Material.COAL,2);var invalidSide=DurableStore.encode(contents[0]);
            click(plugin,player,10,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY,false);
            check(invalidSide.equals(DurableStore.encode(contents[0])),"non-player clicked inventory changed items");
            DurableStore workingStore=plugin.store;plugin.store=new DurableStore(dir.resolve("missing-parent").resolve("state.yml"));
            contents[0][11]=new ItemStack(Material.COAL,2);var beforeFailedSave=DurableStore.encode(contents[0]);
            click(plugin,player,11,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY,true);
            check(beforeFailedSave.equals(DurableStore.encode(contents[0]))&&plugin.store.failed,"failed journal write changed shift-click source");
            plugin.store=workingStore;
            check(cursor[0].equals(new ItemStack(Material.STONE,3)),"shift transfer changed cursor");
            check(saves[0]>=8,"successful shift transfers did not persist player inventory");
        } finally {
            plugin.store=original;
            try(var paths=Files.walk(dir)){for(Path path:paths.sorted((a,b)->b.getNameCount()-a.getNameCount()).toList())Files.deleteIfExists(path);}
        }
    }

    private static void checkShift(ForgeSystemPlugin plugin,Player player,ItemStack[][] contents,int slot,ItemStack item,int target,int amount) {
        contents[0][slot]=item.clone();click(plugin,player,slot,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY,true);
        ItemStack[] input=plugin.store.inputs(player.getUniqueId());
        check(input[target]!=null&&input[target].getAmount()==amount,"wrong input slot or quantity: "+item.getType());
        check(contents[0][slot]==null,"source item was not consumed: "+item.getType());
    }
    private static void rejectUnchanged(ForgeSystemPlugin plugin,Player player,ItemStack[][] contents,int slot,ItemStack item,String scenario) {
        contents[0][slot]=item.clone();var before=DurableStore.encode(contents[0]);var inputs=DurableStore.encode(plugin.store.inputs(player.getUniqueId()));
        click(plugin,player,slot,ClickType.SHIFT_RIGHT,InventoryAction.MOVE_TO_OTHER_INVENTORY,true);
        check(before.equals(DurableStore.encode(contents[0])),scenario+" changed player inventory");
        check(inputs.equals(DurableStore.encode(plugin.store.inputs(player.getUniqueId()))),scenario+" changed forge input");
    }
    private static void setInput(ForgeSystemPlugin plugin,Player player,int slot,ItemStack item) {
        plugin.store.transact(player,state->state.inputs[slot]=item==null?null:item.clone());
    }
    private static void click(ForgeSystemPlugin plugin,Player player,int sourceSlot,ClickType click,InventoryAction action,boolean bottom) {
        Inventory top=Bukkit.createInventory(null,54);Inventory other=Bukkit.createInventory(null,9);
        Inventory bottomWrapper=(Inventory)Proxy.newProxyInstance(Inventory.class.getClassLoader(),new Class<?>[]{Inventory.class},(proxy,method,args)->switch(method.getName()){
            case "getType"->InventoryType.PLAYER;
            case "getHolder"->player;
            default->throw new IllegalStateException("Unexpected wrapped inventory call: "+method.getName());
        });
        InventoryView view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(proxy,method,args)->switch(method.getName()){
            case "getTopInventory"->top;case "getBottomInventory"->bottomWrapper;case "getPlayer"->player;
            case "getInventory"->{int raw=(int)args[0];yield raw<54?top:bottom?bottomWrapper:other;}
            case "convertSlot"->{int raw=(int)args[0];if(raw<54)yield raw;int bottomSlot=raw-54;yield bottomSlot>=27?bottomSlot-27:bottomSlot+9;}
            case "getSlotType"->InventoryType.SlotType.CONTAINER;
            case "getType"->InventoryType.CHEST;
            default->throw new IllegalStateException("Unexpected view call: "+method.getName());
        });
        int raw=54+(sourceSlot<9?27+sourceSlot:sourceSlot-9);InventoryClickEvent event=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,raw,click,action);
        ForgeMenuService.Menu menu=new ForgeMenuService.Menu(player.getUniqueId(),null,"selftest");menu.inventory=top;
        plugin.menus.click(event,player,menu);
        check(event.isCancelled(),"shift-transfer event was not cancelled");
    }
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
