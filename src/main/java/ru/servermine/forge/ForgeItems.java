package ru.servermine.forge;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.persistence.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import ru.servermine.forge.Model.*;

final class ForgeItems implements ForgeItemsApi {
    static final NamespacedKey HAMMER_MODEL=new NamespacedKey("servermine","forge/hammer");
    private final ForgeSystemPlugin plugin;
    private final byte[] secret;
    ForgeItems(ForgeSystemPlugin plugin) throws Exception {
        this.plugin=plugin; Path path=plugin.getDataFolder().toPath().resolve("item-signing.key");
        if(!Files.exists(path)) { byte[] key=new byte[32]; new SecureRandom().nextBytes(key); Files.write(path,key,StandardOpenOption.CREATE_NEW); }
        secret=Files.readAllBytes(path); if(secret.length!=32) throw new IllegalStateException("Invalid signing key");
    }
    static NamespacedKey key(String name) { return new NamespacedKey("forgesystem",name); }
    static boolean empty(ItemStack item) { return item==null||item.getType().isAir()||item.getAmount()==0; }
    static String str(PersistentDataContainer p,String k) { return p.get(key(k),PersistentDataType.STRING); }
    private static void set(PersistentDataContainer p,String k,String v) { if(v==null) p.remove(key(k)); else p.set(key(k),PersistentDataType.STRING,v); }
    String definition(ItemStack item) { return empty(item)?null:str(item.getItemMeta().getPersistentDataContainer(),"item_definition"); }
    boolean technical(ItemStack item) { String d=definition(item); return d!=null&&(!"FINISHED".equals(str(item.getItemMeta().getPersistentDataContainer(),"forge_state"))); }
    boolean locked(ItemStack item) { return readWorkpiece(item).map(w->w.session()!=null).orElse(false); }
    public boolean isHammer(ItemStack item) {
        if(empty(item)||item.getType()!=Material.IRON_AXE||item.getAmount()!=1) return false;
        var p=item.getItemMeta().getPersistentDataContainer();
        return "forgesystem:hammer".equals(str(p,"item_definition"))&&Integer.valueOf(1).equals(p.get(key("item_schema"),PersistentDataType.INTEGER))&&verify("hammer:1",str(p,"signature"));
    }
    public ItemStack createHammer() {
        var item=new ItemStack(Material.IRON_AXE); var meta=item.getItemMeta();
        meta.setItemModel(HAMMER_MODEL);
        meta.displayName(text("Кузнечный молот",NamedTextColor.GOLD)); meta.setUnbreakable(true); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES,ItemFlag.HIDE_UNBREAKABLE);
        meta.lore(List.of(text("ПКМ по горну — открыть",NamedTextColor.GRAY),text("ПКМ по наковальне — ковать",NamedTextColor.GRAY)));
        var p=meta.getPersistentDataContainer(); set(p,"item_definition","forgesystem:hammer"); p.set(key("item_schema"),PersistentDataType.INTEGER,1); set(p,"signature",sign("hammer:1")); item.setItemMeta(meta); return item;
    }
    public Optional<Workpiece> readWorkpiece(ItemStack item) {
        return read(item,true);
    }
    private Optional<Workpiece> read(ItemStack item,boolean signed) {
        if(empty(item)||item.getAmount()!=1) return Optional.empty();
        try {
            var p=item.getItemMeta().getPersistentDataContainer();
            if(!"forgesystem:workpiece".equals(str(p,"item_definition"))||!Integer.valueOf(1).equals(p.get(key("item_schema"),PersistentDataType.INTEGER))) return Optional.empty();
            var w=new Workpiece(UUID.fromString(str(p,"instance_id")),Metal.valueOf(str(p,"material")),Product.valueOf(str(p,"product_type")),State.valueOf(str(p,"forge_state")),Quality.valueOf(str(p,"quality")),
                    p.get(key("quality_stage"),PersistentDataType.INTEGER),p.get(key("current_stage"),PersistentDataType.INTEGER),p.get(key("max_reachable_stage"),PersistentDataType.INTEGER),
                    uuid(str(p,"blacksmith_uuid")),uuid(str(p,"session_id")),p.get(key("temperature_value"),PersistentDataType.DOUBLE),p.get(key("temperature_updated_at_epoch_ms"),PersistentDataType.LONG),str(p,"config_revision"));
            String tier=str(p,"equipment_tier");boolean diamond="DIAMOND".equals(tier);
            if(tier!=null&&(!diamond||w.state()!=State.FINISHED||w.metal()!=Metal.IRON))return Optional.empty();
            if(w.temperature()>9999||w.updated()>System.currentTimeMillis()+60000||item.getType()!=(diamond?Material.valueOf("DIAMOND_"+w.product().name()):w.state()==State.FINISHED?icon(w.metal(),w.product()):Material.PAPER)||(signed&&!verify(signedValue(w,diamond),str(p,"signature")))) return Optional.empty();
            if(w.state()==State.FORGING&&w.session()==null&&w.cap()>w.stage()) {
                var r=plugin.settings.metals.get(w.metal());
                if(w.at(System.currentTimeMillis(),plugin.settings.ambient,r.cooling())<=r.minimum())
                    w=w.progress(w.state(),w.quality(),w.stage(),w.stage(),w.smith(),null,w.temperature(),w.updated());
            }
            return Optional.of(w);
        } catch(IllegalArgumentException|NullPointerException ex) { return Optional.empty(); }
    }
    ItemStack migrate(ItemStack old) {
        if(readWorkpiece(old).isPresent()||isHammer(old))return old;
        if(empty(old))throw new IllegalArgumentException("Возьмите старый предмет в руку.");
        var p=old.getItemMeta().getPersistentDataContainer();
        if(old.getType()==Material.IRON_AXE&&old.getAmount()==1&&Byte.valueOf((byte)1).equals(p.get(new NamespacedKey("forgeminigame","forge_hammer"),PersistentDataType.BYTE)))return createHammer();
        Workpiece w=read(old,false).orElseThrow(()->new IllegalArgumentException("Неизвестная или повреждённая схема: миграция отклонена."));
        if(w.session()!=null)throw new IllegalArgumentException("Сначала завершите старую активную сессию.");
        return encode(old,w);
    }
    static UUID uuid(String s) { return s==null||s.isBlank()?null:UUID.fromString(s); }
    public ItemStack createWorkpiece(Metal metal,Product product,double temperature) {
        var w=new Workpiece(UUID.randomUUID(),metal,product,State.HOT_BLANK,Quality.NONE,0,1,3,null,null,temperature,System.currentTimeMillis(),plugin.settings.revision);
        return encode(new ItemStack(Material.PAPER),w);
    }
    ItemStack change(ItemStack original,Workpiece next) {
        Workpiece old=readWorkpiece(original).orElseThrow(()->new IllegalArgumentException("Invalid item"));
        if(!old.id().equals(next.id())||old.metal()!=next.metal()||old.product()!=next.product()||next.cap()>old.cap()
                ||!Model.transitionAllowed(old.state(),next.state(),old.product().armor())) throw new IllegalArgumentException("Invalid transition");
        return encode(original,next);
    }
    public ItemStack updateThermalAnchor(ItemStack item,double t,long now) {
        Workpiece old=readWorkpiece(item).orElseThrow(); if(!old.reheatAllowed()) throw new IllegalArgumentException("Reheat forbidden");
        return change(item,old.thermal(t,now));
    }
    boolean diamond(ItemStack item) {return !empty(item)&&"DIAMOND".equals(str(item.getItemMeta().getPersistentDataContainer(),"equipment_tier"));}
    ItemStack upgradeToDiamond(ItemStack original) {
        Workpiece w=readWorkpiece(original).orElseThrow(()->new IllegalArgumentException("Нужно готовое кованое железное снаряжение."));
        if(!EquipmentRules.canUpgrade(w,diamond(original)))throw new IllegalArgumentException("Улучшить можно только готовое кованое железное снаряжение.");
        ItemStack result=original.clone();var meta=result.getItemMeta();
        set(meta.getPersistentDataContainer(),"equipment_tier","DIAMOND");result.setItemMeta(meta);
        result=encode(result,w);
        if(original.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable old&&result.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable next) {
            int oldMax=old.hasMaxDamage()?old.getMaxDamage():original.getType().getMaxDurability();
            int newMax=next.hasMaxDamage()?next.getMaxDamage():result.getType().getMaxDurability();
            next.setDamage(EquipmentRules.upgradedDamage(old.getDamage(),oldMax,newMax));result.setItemMeta(next);
        }
        return result;
    }
    ItemStack refreshLore(ItemStack item) {
        if(isHammer(item)) {
            var meta=item.getItemMeta();
            var lore=meta.lore();boolean changed=false;
            if(lore!=null){
                lore=new ArrayList<>(lore);
                for(int i=0;i<lore.size();i++)if(lore.get(i).equals(text("ЛКМ по наковальне — ковать",NamedTextColor.GRAY))){
                    lore.set(i,text("ПКМ по наковальне — ковать",NamedTextColor.GRAY));changed=true;
                }
            }
            if(HAMMER_MODEL.equals(meta.getItemModel())&&!changed)return item;
            if(changed)meta.lore(lore);
            ItemStack updated=item.clone();meta.setItemModel(HAMMER_MODEL);updated.setItemMeta(meta);return updated;
        }
        return readWorkpiece(item).map(w->encode(item,w)).orElse(item);
    }
    private ItemStack encode(ItemStack original,Workpiece w) {
        boolean diamond=diamond(original)&&w.state()==State.FINISHED;
        ItemStack item=original.clone(); item.setType(diamond?Material.valueOf("DIAMOND_"+w.product().name()):w.state()==State.FINISHED?icon(w.metal(),w.product()):Material.PAPER);
        var meta=item.getItemMeta(); var p=meta.getPersistentDataContainer();
        set(p,"item_definition","forgesystem:workpiece"); p.set(key("item_schema"),PersistentDataType.INTEGER,1);
        set(p,"instance_id",w.id().toString()); set(p,"material",w.metal().name()); set(p,"product_type",w.product().name()); set(p,"forge_state",w.state().name()); set(p,"quality",w.quality().name());
        p.set(key("quality_stage"),PersistentDataType.INTEGER,w.qualityStage()); p.set(key("current_stage"),PersistentDataType.INTEGER,w.stage()); p.set(key("max_reachable_stage"),PersistentDataType.INTEGER,w.cap());
        set(p,"blacksmith_uuid",w.smith()==null?null:w.smith().toString()); set(p,"session_id",w.session()==null?null:w.session().toString());
        p.set(key("temperature_value"),PersistentDataType.DOUBLE,w.temperature()); p.set(key("temperature_updated_at_epoch_ms"),PersistentDataType.LONG,w.updated()); set(p,"config_revision",w.revision()); set(p,"signature",sign(signedValue(w,diamond)));
        meta.setMaxStackSize(1);
        if(w.state()!=State.FINISHED||!"FINISHED".equals(str(original.getItemMeta().getPersistentDataContainer(),"forge_state"))||!meta.hasDisplayName())meta.displayName(text(label(w.state())+": "+w.product().label,NamedTextColor.GOLD));
        double temperature=w.at(System.currentTimeMillis(),plugin.settings.ambient,plugin.settings.metals.get(w.metal()).cooling());
        String sprite=ItemSprites.workpiece(w,temperature,plugin.settings.metals.get(w.metal()).minimum());
        meta.setItemModel(sprite==null?null:new NamespacedKey("servermine",sprite));
        var lore=new ArrayList<Component>(); lore.add(text("Материал: "+(diamond?"Алмаз (кованая железная основа)":switch(w.metal()){case IRON->"Железо";case GOLD->"Золото";case COPPER->"Медь";}),NamedTextColor.GRAY));
        lore.add(text("Температура: "+Math.round(temperature)+"°C",NamedTextColor.YELLOW));
        lore.add(text("Качество: "+quality(w.quality())+" | Этап "+w.stage()+" / "+w.cap(),NamedTextColor.GRAY));
        lore.add(text(switch(w.state()) {case HOT_BLANK,FORGING -> "В левую руку; ПКМ молотом по наковальне."; case UNQUENCHED -> "ПКМ по воде — закалить."; case QUENCHED_PART -> "ПКМ по наковальне с палками в левой руке."; case FINISHED -> "Ковка завершена.";},NamedTextColor.GRAY));
        if(EquipmentRules.canUpgrade(w,diamond))lore.add(text("ПКМ по наковальне: "+EquipmentRules.diamonds(w.product())+" алмазов в левой руке — улучшить.",NamedTextColor.AQUA));
        meta.lore(lore); item.setItemMeta(meta); return item;
    }
    static String quality(Quality q) { return switch(q) {case NONE->"Нет";case GOOD->"Хорошая";case EXCELLENT->"Отличная";case MASTERWORK->"Мастерская";}; }
    static String label(State s) { return switch(s) {case HOT_BLANK->"Горячая заготовка";case FORGING->"Формируемая заготовка";case UNQUENCHED->"Незакалённая деталь";case QUENCHED_PART->"Закалённая деталь";case FINISHED->"Кованое изделие";}; }
    static Material icon(Metal m,Product p) { return Material.valueOf((m==Metal.GOLD?"GOLDEN":m.name())+"_"+p.name()); }
    static Component text(String s,NamedTextColor c) { return Component.text(s,c).decoration(TextDecoration.ITALIC,false); }
    static String canonical(Workpiece w) { return "workpiece:1|"+w.id()+"|"+w.metal()+"|"+w.product()+"|"+w.state()+"|"+w.quality()+"|"+w.qualityStage()+"|"+w.stage()+"|"+w.cap()+"|"+w.smith()+"|"+w.session()+"|"+w.temperature()+"|"+w.updated()+"|"+w.revision(); }
    private static String signedValue(Workpiece w,boolean diamond){return canonical(w)+(diamond?"|equipment_tier=DIAMOND":"");}
    private String sign(String value) {
        try { var mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret,"HmacSHA256")); return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))); }
        catch(GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
    private boolean verify(String value,String signature) { return signature!=null&&MessageDigest.isEqual(sign(value).getBytes(StandardCharsets.US_ASCII),signature.getBytes(StandardCharsets.US_ASCII)); }
}
