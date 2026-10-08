package ru.servermine.forge;

import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import ru.servermine.forge.Model.*;

/** Registered with Bukkit ServicesManager by ForgeSystem. All mutations are main-thread only. */
public interface ForgeItemsApi {
    Optional<Workpiece> readWorkpiece(ItemStack item);
    boolean isHammer(ItemStack item);
    ItemStack createHammer();
    ItemStack createWorkpiece(Metal metal,Product product,double temperature);
    ItemStack updateThermalAnchor(ItemStack item,double temperature,long now);
}
