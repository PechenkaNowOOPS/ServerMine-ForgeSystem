package ru.servermine.forge;

import org.bukkit.Material;
import ru.servermine.forge.Model.State;
import ru.servermine.forge.Model.Workpiece;

final class QuenchRules {
    private QuenchRules() {}

    static String nextCauldronData(Material type, int level, int maximum) {
        if (type != Material.WATER_CAULDRON || maximum < 1 || level < 1 || level > maximum)
            throw new IllegalArgumentException("Закалка требует наполненный водой котёл.");
        return level == 1 ? "minecraft:cauldron" : "minecraft:water_cauldron[level=" + (level - 1) + "]";
    }

    static boolean canQuench(Workpiece workpiece, boolean permitted, Material type, int level) {
        return permitted && workpiece != null && workpiece.state() == State.UNQUENCHED
                && type == Material.WATER_CAULDRON && level > 0;
    }
}
