package ru.servermine.forge;

import java.util.Locale;
import ru.servermine.forge.Model.*;

/** Resource IDs only: appearance never changes a workpiece's signed identity. */
final class ItemSprites {
    private ItemSprites() {}
    static String workpiece(Workpiece item, double temperature, double hotThreshold) {
        if (item.state() == State.FINISHED) return null;
        double warmThreshold = Math.min(300.0, hotThreshold / 2);
        String heat = temperature >= hotThreshold ? "hot" : temperature >= warmThreshold ? "warm" : "cold";
        return prefix(item.metal(), item.product()) + heat;
    }

    private static String prefix(Metal metal, Product product) {
        return "forge/workpiece/" + metal.name().toLowerCase(Locale.ROOT) + "/" + product.name().toLowerCase(Locale.ROOT) + "/";
    }
}
