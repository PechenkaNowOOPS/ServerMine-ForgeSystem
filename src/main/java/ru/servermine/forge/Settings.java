package ru.servermine.forge;

import java.io.File;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.servermine.forge.Model.*;

final class Settings {
    record MetalRule(double initial,double minimum,double cooling,double maximum) {}
    record Recipe(int ingots,int sticks) {}
    record BlockRule(int x,int y,int z,Material material) {}
    final double ambient,working,maximum,heat,cool;
    final int physics,visual,save;
    final long cooldown;
    final String revision;
    final Map<Material,Double> fuels=new EnumMap<>(Material.class);
    final Map<Metal,MetalRule> metals=new EnumMap<>(Metal.class);
    final Map<Product,Recipe> recipes=new EnumMap<>(Product.class);
    final List<BlockRule> blocks=new ArrayList<>();
    final ForgeSession.Rule[] stages=new ForgeSession.Rule[3];
    final YamlConfiguration messages,pack,gui;
    Settings(File dir) throws Exception {
        var c=read(dir,"config"); var m=read(dir,"materials"); var p=read(dir,"products"); var b=read(dir,"multiblocks");
        messages=read(dir,"messages"); pack=read(dir,"resourcepack"); gui=read(dir,"gui");
        boolean addCopper=!m.contains("COPPER");
        if(addCopper){
            try(var reader=new java.io.InputStreamReader(Objects.requireNonNull(Settings.class.getResourceAsStream("/materials.yml")),java.nio.charset.StandardCharsets.UTF_8)){
                var defaults=YamlConfiguration.loadConfiguration(reader).getConfigurationSection("COPPER");
                Objects.requireNonNull(defaults).getValues(false).forEach((key,value)->m.set("COPPER."+key,value));
            }
        }
        boolean oldMetalMessage="Нужны обычные железные или золотые слитки.".equals(messages.getString("unsupported-metal"));
        if(oldMetalMessage)messages.set("unsupported-metal","Нужны обычные железные, золотые или медные слитки.");
        ambient=c.getDouble("station.ambient-temperature"); working=c.getDouble("station.working-temperature"); maximum=c.getDouble("station.maximum-temperature");
        heat=c.getDouble("station.heating-per-second"); cool=c.getDouble("station.cooling-per-second");
        if(!(0<=ambient&&ambient<working&&working<maximum&&maximum<=9999&&heat>0&&cool>0)
                ||!Double.isFinite(maximum)||!Double.isFinite(heat)||!Double.isFinite(cool)) throw new IllegalArgumentException("Invalid station temperatures");
        physics=c.getInt("station.manager-period-ticks",20); visual=gui.getInt("forge.temperature.visual-update-period-ticks",5); save=c.getInt("station.save-period-ticks",200);
        cooldown=c.getLong("operation-cooldown-ms",300); revision=c.getString("config-revision","forge-v3");
        if(physics<1||visual<1||save<20||cooldown<0) throw new IllegalArgumentException("Invalid scheduler settings");
        if(c.getBoolean("hammer.durability.enabled")) throw new IllegalArgumentException("Hammer durability not enabled in this release; leave enabled: false");
        for(String name:Objects.requireNonNull(c.getConfigurationSection("fuel")).getKeys(false)) {
            double v=c.getDouble("fuel."+name); if(!Double.isFinite(v)||v<=0) throw new IllegalArgumentException("Invalid fuel"); fuels.put(Material.valueOf(name),v);
        }
        for(Metal metal:Metal.values()) {
            String k=metal.name()+"."; var r=new MetalRule(m.getDouble(k+"initial-after-forge"),m.getDouble(k+"minimum-working"),m.getDouble(k+"cooling-per-second"),m.getDouble(k+"maximum-safe"));
            if(!(ambient<r.minimum&&r.minimum<r.initial&&r.initial<=r.maximum&&r.maximum<=9999&&r.cooling>0)||!Double.isFinite(r.cooling)) throw new IllegalArgumentException("Invalid material "+metal);
            metals.put(metal,r);
        }
        for(Product product:Product.values()) {
            int ingots=p.getInt(product+".ingots"),sticks=p.getInt(product+".sticks");
            if(ingots<1||ingots>64||sticks<0||sticks>64||(product.armor()?sticks!=0:sticks==0)) throw new IllegalArgumentException("Invalid product "+product);
            recipes.put(product,new Recipe(ingots,sticks));
            String path="forge.slots."+(product.armor()?"armor.":"tools.")+product.name().toLowerCase(Locale.ROOT);
            if(gui.getInt(path)!=product.slot) throw new IllegalArgumentException("v3 geometry requires "+path+" = "+product.slot);
        }
        Map<String,Integer> slots=Map.ofEntries(Map.entry("fuel-input",10),Map.entry("ingot-input",12),Map.entry("reheat-input",14),Map.entry("temperature-fallback",16),Map.entry("load-fuel",19),Map.entry("fuel-reserve",20),Map.entry("status",21),Map.entry("reheat",23),Map.entry("help",25),Map.entry("close",53));
        slots.forEach((k,v)->{if(gui.getInt("forge.slots."+k)!=v) throw new IllegalArgumentException("Invalid v3 slot "+k);});
        if(gui.getInt("forge.size")!=54||gui.getInt("forge.temperature.gauge-frames")!=21||!b.getString("core","").equals("BLAST_FURNACE")) throw new IllegalArgumentException("Invalid v3 GUI or core");
        for(var r:b.getMapList("blocks")) blocks.add(new BlockRule(((Number)r.get("x")).intValue(),((Number)r.get("y")).intValue(),((Number)r.get("z")).intValue(),Material.valueOf(r.get("material").toString())));
        for(int i=0;i<3;i++) { String k="stages.stage-"+(i+1)+"."; stages[i]=new ForgeSession.Rule(c.getDouble(k+"zone-width"),c.getDouble(k+"one-way-seconds"),c.getDouble(k+"min-center-shift"),c.getDouble(k+"edge-padding")); }
        // Persist additions only after the full configuration has passed validation.
        if(addCopper)m.save(new File(dir,"materials.yml"));
        if(oldMetalMessage)messages.save(new File(dir,"messages.yml"));
    }
    static YamlConfiguration read(File dir,String name) throws Exception { var y=new YamlConfiguration(); y.load(new File(dir,name+".yml")); return y; }
    String message(String key) { return messages.getString(key,key); }
}
