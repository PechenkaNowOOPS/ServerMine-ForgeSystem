package ru.servermine.forge;

import java.util.*;
import org.bukkit.Material;
import ru.servermine.forge.Model.*;

final class EquipmentRules {
    private EquipmentRules() {}
    static boolean blockedCraft(Material material) {
        for(String tier:new String[]{"COPPER","IRON","GOLDEN","DIAMOND"})
            for(Product product:Product.values())if(material.name().equals(tier+"_"+product.name()))return true;
        return false;
    }
    static boolean canUpgrade(Workpiece item,boolean diamond) {
        return item.metal()==Metal.IRON&&item.state()==State.FINISHED&&item.session()==null&&!diamond;
    }
    static int diamonds(Product product) {
        return switch(product){case SWORD,HOE->2;case PICKAXE,AXE->3;case SHOVEL->1;case HELMET->5;case CHESTPLATE->8;case LEGGINGS->7;case BOOTS->4;};
    }
    static int upgradedDamage(int damage,int oldMaximum,int newMaximum) {
        if(damage<=0)return 0;
        return Math.min(newMaximum-1,(int)Math.ceil(damage*(double)newMaximum/oldMaximum));
    }
}
