package ru.servermine.forge;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.*;

/** Remove crafting recipes from the recipe book; event checks also cover late additions. */
final class EquipmentRecipes {
    private final Map<NamespacedKey,Recipe> removed=new LinkedHashMap<>();
    void remove() {
        var recipes=Bukkit.recipeIterator();
        while(recipes.hasNext()) {
            Recipe recipe=recipes.next();
            if(recipe instanceof CraftingRecipe&&recipe instanceof Keyed keyed&&EquipmentRules.blockedCraft(recipe.getResult().getType())) {
                removed.putIfAbsent(keyed.getKey(),recipe);recipes.remove();
            }
        }
    }
    void restore() {
        removed.forEach((key,recipe)->{if(Bukkit.getRecipe(key)==null)Bukkit.addRecipe(recipe);});removed.clear();
    }
}
