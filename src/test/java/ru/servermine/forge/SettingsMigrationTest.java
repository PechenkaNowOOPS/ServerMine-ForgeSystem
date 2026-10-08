package ru.servermine.forge;

import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.servermine.forge.Model.Metal;
import static org.junit.jupiter.api.Assertions.*;

class SettingsMigrationTest {
    @TempDir Path dir;
    void defaults() throws Exception {
        for(String name:new String[]{"config","materials","products","multiblocks","gui","messages","resourcepack"})
            try(var input=getClass().getResourceAsStream("/"+name+".yml")){Files.copy(input,dir.resolve(name+".yml"));}
    }
    @Test void upgradeAddsCopperAndPreservesCustomSettings() throws Exception {
        defaults();var materials=Settings.read(dir.toFile(),"materials");
        materials.set("COPPER",null);materials.set("IRON.cooling-per-second",17.5);materials.save(dir.resolve("materials.yml").toFile());
        var messages=Settings.read(dir.toFile(),"messages");messages.set("unsupported-metal","Мой текст");messages.save(dir.resolve("messages.yml").toFile());
        var settings=new Settings(dir.toFile());
        assertEquals(550,settings.metals.get(Metal.COPPER).minimum());
        assertEquals(17.5,settings.metals.get(Metal.IRON).cooling());
        assertEquals("Мой текст",settings.message("unsupported-metal"));
        assertTrue(Settings.read(dir.toFile(),"materials").isConfigurationSection("COPPER"));
        assertEquals(settings.metals,new Settings(dir.toFile()).metals);
    }
    @Test void existingCopperValuesArePreserved() throws Exception {
        defaults();var materials=Settings.read(dir.toFile(),"materials");
        materials.set("COPPER.minimum-working",580);materials.save(dir.resolve("materials.yml").toFile());
        assertEquals(580,new Settings(dir.toFile()).metals.get(Metal.COPPER).minimum());
    }
    @Test void invalidExistingConfigurationDoesNotGetWrittenDuringUpgrade() throws Exception {
        defaults();var materials=Settings.read(dir.toFile(),"materials");
        materials.set("COPPER",null);materials.set("IRON.minimum-working",2000);materials.save(dir.resolve("materials.yml").toFile());
        String original=Files.readString(dir.resolve("materials.yml"));
        assertThrows(IllegalArgumentException.class,()->new Settings(dir.toFile()));
        assertEquals(original,Files.readString(dir.resolve("materials.yml")));
    }
}
