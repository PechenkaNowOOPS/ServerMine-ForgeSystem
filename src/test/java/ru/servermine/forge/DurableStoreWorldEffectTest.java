package ru.servermine.forge;

import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DurableStoreWorldEffectTest {
    @Test void pendingWorldEffectSurvivesReloadWithExactTargetAndOrigin() throws Exception {
        var dir=Files.createTempDirectory("forge-world-effect-");var path=dir.resolve("state.yml");UUID world=UUID.randomUUID();
        try {
            var effect=new DurableStore.WorldEffect(world,17,64,-23,"minecraft:water_cauldron[level=3]","minecraft:water_cauldron[level=2]");
            var store=new DurableStore(path);store.writeWorldEffect("players.test.pending.world-effect",effect);store.save();
            var recovered=new DurableStore(path).readWorldEffect("players.test.pending.world-effect");
            assertEquals(effect,recovered);
        } finally {Files.deleteIfExists(path);Files.deleteIfExists(path.resolveSibling("state.yml.tmp"));Files.deleteIfExists(dir);}
    }
}
