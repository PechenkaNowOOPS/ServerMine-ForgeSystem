package ru.servermine.forge;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** Bounded, opt-in observation of real click events; never changes inventory or cancellation. */
final class ShiftClickTrace implements Listener {
    private record Before(long started, String source, boolean cancelled) {}
    private final ForgeSystemPlugin plugin;
    private final Map<InventoryClickEvent,Before> pending=new WeakHashMap<>();
    private long until;
    private int remaining;
    ShiftClickTrace(ForgeSystemPlugin plugin){this.plugin=plugin;}
    void enable(boolean enabled){until=enabled?System.nanoTime()+600_000_000_000L:0;remaining=enabled?100:0;pending.clear();}

    @EventHandler(priority=EventPriority.LOWEST)
    public void before(InventoryClickEvent event){
        if(remaining<=0||System.nanoTime()>until||!event.isShiftClick()
                ||!(event.getView().getTopInventory().getHolder() instanceof ForgeMenuService.Menu))return;
        remaining--;
        pending.put(event,new Before(System.nanoTime(),item(event.getCurrentItem()),event.isCancelled()));
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void after(InventoryClickEvent event){
        Before before=pending.remove(event);
        if(before==null||!(event.getWhoClicked() instanceof Player player))return;
        long finished=System.nanoTime();
        var menu=(ForgeMenuService.Menu)event.getView().getTopInventory().getHolder();
        int raw=event.getRawSlot(),slot=event.getSlot();
        String tag="[ShiftTrace] player="+player.getName()+" raw="+raw+" slot="+slot;
        plugin.getLogger().info(tag+" converted="+event.getView().convertSlot(raw)
                +" click="+event.getClick()+" action="+event.getAction()
                +" cancelledBefore="+before.cancelled()+" cancelledAfter="+event.isCancelled()
                +" sourceBefore="+before.source()+" sourceAfter="+item(event.getCurrentItem())
                +" eventMs="+String.format(Locale.ROOT,"%.3f",(finished-before.started())/1_000_000.0)
                +" storageFailed="+plugin.store.failed+state(player,menu));
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!player.isOnline()){plugin.getLogger().info(tag+" nextTick=offline");return;}
            plugin.getLogger().info(tag+" nextTickMs="+String.format(Locale.ROOT,"%.3f",(System.nanoTime()-finished)/1_000_000.0)
                    +" active="+plugin.menus.active(player,menu)+state(player,menu));
        });
    }
    private String state(Player player,ForgeMenuService.Menu menu){
        ItemStack[] inputs=plugin.store.inputs(player.getUniqueId());
        return " escrow="+item(inputs[0])+","+item(inputs[1])+","+item(inputs[2])
                +" gui10/12/14="+item(menu.inventory.getItem(10))+","+item(menu.inventory.getItem(12))+","+item(menu.inventory.getItem(14));
    }
    private static String item(ItemStack item){return ForgeItems.empty(item)?"empty":item.getType()+"x"+item.getAmount();}
}
