package ru.servermine.forge;

import java.util.*;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import ru.servermine.forge.Model.*;

final class EquipmentSelfTest {
    static void run(ForgeSystemPlugin plugin) {
        var items=plugin.items;
        for(Metal metal:Metal.values())for(Product product:Product.values()) {
            ItemStack blank=items.createWorkpiece(metal,product,950);Workpiece w=items.readWorkpiece(blank).orElseThrow();
            reject(()->items.upgradeToDiamond(blank));
            UUID smith=UUID.randomUUID();
            Workpiece active=w.progress(State.FORGING,Quality.NONE,1,3,smith,UUID.randomUUID(),900,w.updated());
            ItemStack current=items.change(blank,active);
            Workpiece unquenched=active.progress(State.UNQUENCHED,Quality.GOOD,1,1,smith,null,800,w.updated());current=items.change(current,unquenched);
            Workpiece ready=unquenched.progress(product.armor()?State.FINISHED:State.QUENCHED_PART,Quality.GOOD,1,1,smith,null,20,w.updated());current=items.change(current,ready);
            if(!product.armor()){ready=ready.progress(State.FINISHED,Quality.GOOD,1,1,smith,null,20,w.updated());current=items.change(current,ready);}
            ItemStack finished=current;
            if(metal!=Metal.IRON){reject(()->items.upgradeToDiamond(finished));continue;}
            var meta=finished.getItemMeta();meta.displayName(Component.text("Именное снаряжение"));meta.addEnchant(Enchantment.UNBREAKING,2,true);
            var extra=new NamespacedKey("test","extra");meta.getPersistentDataContainer().set(extra,PersistentDataType.STRING,"preserved");
            ((Damageable)meta).setDamage(finished.getType().getMaxDurability()/2);finished.setItemMeta(meta);
            ItemStack[] inventory=new ItemStack[41];inventory[0]=finished.clone();inventory[40]=new ItemStack(Material.DIAMOND,EquipmentRules.diamonds(product)+1);
            plugin.forging.upgradeInventory(inventory,0);ItemStack upgraded=inventory[0];
            require(inventory[40].getAmount()==1,"Incorrect diamond cost");
            require(upgraded.getType()==Material.valueOf("DIAMOND_"+product),"Wrong upgraded material");
            require(ready.equals(items.readWorkpiece(upgraded).orElseThrow()),"Upgrade changed identity, quality or progress");
            require(upgraded.getEnchantments().equals(finished.getEnchantments())&&Objects.equals(upgraded.getItemMeta().displayName(),meta.displayName()),"Name/enchantment lost");
            require("preserved".equals(upgraded.getItemMeta().getPersistentDataContainer().get(extra,PersistentDataType.STRING)),"Foreign metadata lost");
            require(((Damageable)upgraded.getItemMeta()).getDamage()==EquipmentRules.upgradedDamage(((Damageable)meta).getDamage(),finished.getType().getMaxDurability(),upgraded.getType().getMaxDurability()),"Wear changed");
            ItemStack refreshed=items.refreshLore(DurableStore.decode(DurableStore.encode(upgraded)));
            require(refreshed.getType()==upgraded.getType()&&ready.equals(items.readWorkpiece(refreshed).orElseThrow()),"Diamond tier lost on reload/refresh");
            reject(()->plugin.forging.upgradeInventory(inventory,0));require(inventory[40].getAmount()==1,"Repeated upgrade consumed diamonds");
            inventory[0]=finished.clone();inventory[40]=EquipmentRules.diamonds(product)>1?new ItemStack(Material.DIAMOND,EquipmentRules.diamonds(product)-1):null;
            ItemStack[] before=Arrays.stream(inventory).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);
            reject(()->plugin.forging.upgradeInventory(inventory,0));require(Arrays.equals(before,inventory),"Failed upgrade mutated inventory");
            inventory[40]=new ItemStack(Material.DIAMOND,EquipmentRules.diamonds(product));plugin.forging.upgradeInventory(inventory,0);require(inventory[40]==null,"Exact payment not consumed");
            var forged=finished.clone();forged.setType(Material.valueOf("DIAMOND_"+product));var fake=forged.getItemMeta();fake.getPersistentDataContainer().set(ForgeItems.key("equipment_tier"),PersistentDataType.STRING,"DIAMOND");forged.setItemMeta(fake);
            require(items.readWorkpiece(forged).isEmpty(),"Unsigned diamond tier accepted");
        }
        reject(()->items.upgradeToDiamond(new ItemStack(Material.IRON_PICKAXE)));
        var recipes=Bukkit.recipeIterator();while(recipes.hasNext()){Recipe recipe=recipes.next();if(recipe instanceof CraftingRecipe)require(!EquipmentRules.blockedCraft(recipe.getResult().getType()),"Blocked recipe remains registered");}
        require(Bukkit.getRecipe(NamespacedKey.minecraft("wooden_pickaxe"))!=null&&Bukkit.getRecipe(NamespacedKey.minecraft("stone_pickaxe"))!=null,"Wood/stone recipes removed");
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void reject(Runnable action){try{action.run();}catch(IllegalArgumentException expected){return;}throw new IllegalStateException("Invalid upgrade accepted");}
}
