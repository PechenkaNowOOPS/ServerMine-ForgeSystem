package ru.servermine.forge;

import org.bukkit.Material;
import ru.servermine.forge.Model.State;
import ru.servermine.forge.Model.Workpiece;

final class QuenchRules {
    static final String HELP="Возьмите незакалённую деталь в основную руку и нажмите ПКМ по наполненному водой котлу. Одна закалка расходует один уровень воды.";
    static final String ITEM_HELP="ПКМ по наполненному водой котлу — закалить (−1 уровень).";
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
