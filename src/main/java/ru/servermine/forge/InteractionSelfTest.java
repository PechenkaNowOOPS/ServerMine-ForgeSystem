package ru.servermine.forge;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Exercises the real event handler with isolated inventories; never touches player data. */
final class InteractionSelfTest {
    static void run(ForgeSystemPlugin plugin) {
        ItemStack[] hands={plugin.items.createHammer(),plugin.items.createWorkpiece(Model.Metal.IRON,Model.Product.PICKAXE,1000)};
        var attempts=new AtomicInteger();var id=UUID.randomUUID();
        PlayerInventory inventory=proxy(PlayerInventory.class,(p,m,a)->switch(m.getName()) {
            case "getItemInMainHand" -> hands[0];case "getItemInOffHand" -> hands[1];
            default -> throw new IllegalStateException("Unexpected inventory call: "+m.getName());
        });
        for(Material type:new Material[]{Material.ANVIL,Material.CHIPPED_ANVIL,Material.DAMAGED_ANVIL}) {
            Block block=proxy(Block.class,(p,m,a)->switch(m.getName()) {
                case "getType" -> type;default -> throw new IllegalStateException("Unexpected block call: "+m.getName());
            });
            Player player=proxy(Player.class,(p,m,a)->switch(m.getName()) {
                case "getInventory" -> inventory;case "getUniqueId" -> id;
                case "getTargetBlockExact" -> block;
                // Stop strike before a transaction; count how often the handler reaches it.
                case "hasPermission" -> {attempts.incrementAndGet();yield false;}
                default -> throw new IllegalStateException("Unexpected player call: "+m.getName());
            });
            int before=attempts.get();
            var right=event(player,Action.RIGHT_CLICK_BLOCK,block,EquipmentSlot.HAND,hands[0]);plugin.interact(right);
            if(!right.isCancelled()||attempts.get()!=before+1)throw new IllegalStateException("Right click did not route to forging");
            var off=event(player,Action.RIGHT_CLICK_BLOCK,block,EquipmentSlot.OFF_HAND,hands[1]);plugin.interact(off);
            var left=event(player,Action.LEFT_CLICK_BLOCK,block,EquipmentSlot.HAND,hands[0]);plugin.interact(left);
            if(off.isCancelled()||left.isCancelled()||attempts.get()!=before+1)throw new IllegalStateException("Duplicate or left-click strike");
            var hammer=hands[0];hands[0]=new ItemStack(Material.IRON_AXE);
            var vanilla=event(player,Action.RIGHT_CLICK_BLOCK,block,EquipmentSlot.HAND,hands[0]);plugin.interact(vanilla);
            if(vanilla.isCancelled()||attempts.get()!=before+1)throw new IllegalStateException("Vanilla axe interaction intercepted");
            hands[0]=hammer;
        }
    }
    private static PlayerInteractEvent event(Player p,Action action,Block block,EquipmentSlot hand,ItemStack item) {
        return new PlayerInteractEvent(p,action,item,block,BlockFace.UP,hand);
    }
    private static <T> T proxy(Class<T> type,java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler));
    }
}
