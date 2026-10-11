package ru.servermine.forge;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import ru.servermine.forge.Model.*;

final class ForgeMenuService {
    enum Status {
        COLD("ХОЛОДНЫЙ"),HEATING("НАГРЕВАЕТСЯ"),WORKING("ГОТОВ К РАБОТЕ"),COOLING("ОСТЫВАЕТ"),
        NO_FUEL("НЕТ ТОПЛИВА"),NOT_ENOUGH("НЕДОСТАТОЧНО МЕТАЛЛА"),WRONG_METAL("НЕПОДХОДЯЩИЙ МЕТАЛЛ"),CREATED("ЗАГОТОВКА СОЗДАНА"),
        REHEATING("ПОВТОРНЫЙ НАГРЕВ"),REHEATED("ЗАГОТОВКА НАГРЕТА"),INVALID_ITEM("НЕВЕРНАЯ ЗАГОТОВКА"),LOCKED("АКТИВНАЯ СЕССИЯ КОВКИ"),INVALID_STATION("ГОРН ПОВРЕЖДЁН");
        final String text; Status(String text){this.text=text;}
    }
    record Snapshot(int frame,int temperature,Status status,boolean canWork,boolean pack,FuelDisplay fuel) {}
    record TitleSnapshot(int frame,int temperature,Status status,boolean pack,String clockDigits,String coalDigits,boolean overflow) {
        static TitleSnapshot of(Snapshot s) {
            return new TitleSnapshot(s.frame,displayTemperature(s.temperature),s.status,s.pack,
                    s.fuel.clockDigits(),s.fuel.coalDigits(),s.fuel.timeOverflow());
        }
    }
    static int displayTemperature(int temperature) { return (int)Math.round(temperature/25.0)*25; }
    static final class Menu implements InventoryHolder {
        final UUID viewer; final Block core; final String station; Inventory inventory;
        TitleSnapshot titleSnapshot; String recipes=""; Status feedback; long feedbackUntil; int priority;
        final Map<String,ItemStack> staticButtons=new HashMap<>();
        final Map<Integer,String> dynamicKeys=new HashMap<>();
        ItemStack cachedReheatSource,cachedReheatDisplay; int cachedReheatTemperature=Integer.MIN_VALUE; String cachedReheatSprite;
        boolean closed;
        Menu(UUID viewer,Block core,String station){this.viewer=viewer;this.core=core;this.station=station;}
        public Inventory getInventory(){return inventory;}
    }
    final ForgeSystemPlugin plugin;
    final Map<UUID,Menu> menus=new HashMap<>();
    final Map<UUID,Long> operations=new HashMap<>();
    ForgeMenuService(ForgeSystemPlugin plugin){this.plugin=plugin;}
    static int inputIndex(int slot) {return switch(slot){case 10->0;case 12->1;case 14->2;default->-1;};}
    boolean valid(Block core){
        if(!core.getWorld().isChunkLoaded(core.getX()>>4,core.getZ()>>4)||core.getType()!=Material.BLAST_FURNACE)return false;
        for(var r:plugin.settings.blocks){int x=core.getX()+r.x(),z=core.getZ()+r.z(); if(!core.getWorld().isChunkLoaded(x>>4,z>>4)||core.getRelative(r.x(),r.y(),r.z()).getType()!=r.material())return false;}
        return true;
    }
    private String key(Block b){return b.getWorld().getUID()+"_"+b.getX()+"_"+b.getY()+"_"+b.getZ();}
    TemperatureModel state(String id){
        String k="stations."+id+"."; var d=plugin.store.data; long now=System.currentTimeMillis();
        var s=new TemperatureModel(d.getDouble(k+"temperature",plugin.settings.ambient),d.getDouble(k+"fuel",0),d.getLong(k+"updated",now));
        s.update(now,plugin.settings.ambient,plugin.settings.maximum,plugin.settings.heat,plugin.settings.cool); return s;
    }
    void write(String id,TemperatureModel s){String k="stations."+id+"."; var d=plugin.store.data;d.set(k+"temperature",s.temperature);d.set(k+"fuel",s.fuel);d.set(k+"updated",s.updated);}
    void physics(){var section=plugin.store.data.getConfigurationSection("stations");if(section!=null)for(String id:section.getKeys(false))write(id,state(id));}
    void open(Player p,Block core){
        if(!p.hasPermission("forgesystem.use")){p.sendMessage(plugin.settings.message("no-permission"));return;}
        if(!valid(core)){p.sendMessage(plugin.settings.message("invalid-station"));return;}
        p.closeInventory();
        String id=key(core);write(id,state(id));plugin.store.save();
        Menu m=new Menu(p.getUniqueId(),core,id); m.inventory=Bukkit.createInventory(m,54,"Кузнечный горн");
        p.openInventory(m.inventory);menus.put(p.getUniqueId(),m);render(p,m);
    }
    boolean active(Player p,Menu m){return !m.closed&&menus.get(p.getUniqueId())==m&&p.getOpenInventory().getTopInventory()==m.inventory;}
    boolean allowedInput(int index,ItemStack item){
        if(ForgeItems.empty(item))return true;
        return switch(index){case 0->!item.hasItemMeta()&&plugin.settings.fuels.containsKey(item.getType());case 1->metal(item)!=null;case 2->plugin.items.readWorkpiece(item).map(Workpiece::reheatAllowed).orElse(false);default->false;};
    }
    int shiftInput(ItemStack item) {
        if(ForgeItems.empty(item))return -1;
        return MenuInputTransfer.target(allowedInput(0,item),allowedInput(1,item),allowedInput(2,item));
    }
    void shiftTransfer(Player player,int sourceSlot) {
        plugin.store.transact(player,state->{
            if(sourceSlot<0||sourceSlot>=36)throw new IllegalArgumentException("Выберите предмет в основном инвентаре или хотбаре.");
            ItemStack source=state.contents[sourceSlot];int target=shiftInput(source);
            if(target<0||!MenuInputTransfer.move(state.contents,state.inputs,sourceSlot,target,item->allowedInput(target,item)))
                throw new IllegalArgumentException("Предмет не подходит или вход горна занят.");
        });
    }
    Metal metal(ItemStack i){if(ForgeItems.empty(i)||i.hasItemMeta())return null;return switch(i.getType()){case IRON_INGOT->Metal.IRON;case GOLD_INGOT->Metal.GOLD;case COPPER_INGOT->Metal.COPPER;default->null;};}
    void click(InventoryClickEvent e,Player p,Menu m){
        int raw=e.getRawSlot(),index=inputIndex(raw);
        // Collect-to-cursor can otherwise steal recipe/indicator items from the top inventory.
        if(e.getClick()==ClickType.DOUBLE_CLICK||e.getAction()==InventoryAction.COLLECT_TO_CURSOR){e.setCancelled(true);return;}
        if(raw<0)return;
        if(raw>=54){
            if(e.isShiftClick()){
                e.setCancelled(true);
                int slot=MenuInputTransfer.sourceSlot(e.getClickedInventory()==p.getInventory()&&e.getView().getBottomInventory()==p.getInventory(),raw,e.getSlot(),
                        e.getView().convertSlot(raw),e.getView().getTopInventory().getSize(),e.getClick(),e.getAction());
                if(slot<0)return;
                plugin.safe(p,()->shiftTransfer(p,slot));render(p,m);
            }
            return;
        }
        e.setCancelled(true);if(!active(p,m)||plugin.store.failed)return;
        if(index>=0){
            if(e.isShiftClick()){plugin.safe(p,()->plugin.store.transact(p,s->{if(!s.add(s.inputs[index]))throw new IllegalArgumentException(plugin.settings.message("full-inventory"));s.inputs[index]=null;}));}
            else if(e.getClick()==ClickType.LEFT||e.getClick()==ClickType.RIGHT){boolean right=e.getClick()==ClickType.RIGHT;
                plugin.safe(p,()->plugin.store.transact(p,s->{ItemStack in=s.inputs[index],cursor=s.cursor;
                    if(ForgeItems.empty(cursor)){if(ForgeItems.empty(in))return;int take=right?(in.getAmount()+1)/2:in.getAmount();s.cursor=in.clone();s.cursor.setAmount(take);in.setAmount(in.getAmount()-take);if(in.getAmount()==0)s.inputs[index]=null;}
                    else {if(!allowedInput(index,cursor))throw new IllegalArgumentException("Этот предмет не подходит для входа.");
                        if(ForgeItems.empty(in)){int n=right?1:cursor.getAmount();s.inputs[index]=cursor.clone();s.inputs[index].setAmount(n);cursor.setAmount(cursor.getAmount()-n);if(cursor.getAmount()==0)s.cursor=null;}
                        else if(in.isSimilar(cursor)){int n=Math.min(right?1:cursor.getAmount(),in.getMaxStackSize()-in.getAmount());in.setAmount(in.getAmount()+n);cursor.setAmount(cursor.getAmount()-n);if(cursor.getAmount()==0)s.cursor=null;}
                        else if(!right){s.inputs[index]=cursor;s.cursor=in;}
                    }}));
            }
            render(p,m);return;
        }
        if(e.getClick()!=ClickType.LEFT&&e.getClick()!=ClickType.SHIFT_LEFT)return;
        if(raw==53){Bukkit.getScheduler().runTask(plugin,() -> p.closeInventory());return;}
        if(raw==25){plugin.help(p);return;}
        long now=System.currentTimeMillis();if(now-operations.getOrDefault(p.getUniqueId(),0L)<plugin.settings.cooldown)return;operations.put(p.getUniqueId(),now);
        if(!valid(m.core)){feedback(m,Status.INVALID_STATION,2);render(p,m);Bukkit.getScheduler().runTask(plugin,() -> p.closeInventory());return;}
        if(raw==19)load(p,m,e.isShiftClick());else if(raw==23)reheat(p,m);else {Product product=Product.at(raw);if(product!=null)create(p,m,product);}
        render(p,m);
    }
    void load(Player p,Menu m,boolean all){plugin.safe(p,()->plugin.store.transact(p,s->{var item=s.inputs[0];if(ForgeItems.empty(item)||!allowedInput(0,item)){feedback(m,Status.NO_FUEL,2);throw new IllegalArgumentException(plugin.settings.message("no-fuel"));}int n=all?item.getAmount():1;
        var station=state(m.station);station.fuel+=plugin.settings.fuels.get(item.getType())*n;write(m.station,station);consume(s.inputs,0,n);}));}
    void create(Player p,Menu m,Product product){plugin.safe(p,()->plugin.store.transact(p,s->{var station=state(m.station);
        if(!station.canWork(plugin.settings.working)){feedback(m,Status.COLD,2);throw new IllegalArgumentException(plugin.settings.message("too-cold"));}
        var metal=metal(s.inputs[1]);if(metal==null){feedback(m,Status.WRONG_METAL,2);throw new IllegalArgumentException(plugin.settings.message("unsupported-metal"));}
        int cost=plugin.settings.recipes.get(product).ingots();if(s.inputs[1].getAmount()<cost){feedback(m,Status.NOT_ENOUGH,2);throw new IllegalArgumentException(plugin.settings.message("not-enough-metal"));}
        var result=plugin.items.createWorkpiece(metal,product,Math.min(station.temperature,plugin.settings.metals.get(metal).initial()));
        if(!s.add(result))throw new IllegalArgumentException(plugin.settings.message("full-inventory"));consume(s.inputs,1,cost);feedback(m,Status.CREATED,1);}));}
    void reheat(Player p,Menu m){plugin.safe(p,()->plugin.store.transact(p,s->{var station=state(m.station);
        if(!station.canWork(plugin.settings.working)){feedback(m,Status.COLD,2);throw new IllegalArgumentException(plugin.settings.message("too-cold"));}
        Workpiece w=plugin.items.readWorkpiece(s.inputs[2]).orElseThrow(()->new IllegalArgumentException(plugin.settings.message("invalid-workpiece")));
        if(!w.reheatAllowed()){feedback(m,w.session()==null?Status.INVALID_ITEM:Status.LOCKED,2);throw new IllegalArgumentException(plugin.settings.message("invalid-workpiece"));}
        s.inputs[2]=plugin.items.updateThermalAnchor(s.inputs[2],Math.min(station.temperature,plugin.settings.metals.get(w.metal()).initial()),System.currentTimeMillis());feedback(m,Status.REHEATED,1);}));}
    static void consume(ItemStack[] arr,int n,int amount){arr[n].setAmount(arr[n].getAmount()-amount);if(arr[n].getAmount()==0)arr[n]=null;}
    void feedback(Menu m,Status s,int priority){if(m.feedbackUntil<=System.currentTimeMillis()||priority>=m.priority){m.feedback=s;m.priority=priority;m.feedbackUntil=System.currentTimeMillis()+1800;}}
    void render(Player p,Menu m){
        if(!active(p,m))return;var s=state(m.station);var cfg=plugin.settings;boolean ready=s.canWork(cfg.working),pack=plugin.pack.loaded(p)&&cfg.gui.getBoolean("forge.visuals.custom-background");
        Status status=!valid(m.core)?Status.INVALID_STATION:m.feedbackUntil>System.currentTimeMillis()?m.feedback:Status.valueOf(s.status(cfg.ambient,cfg.working).name());
        FuelDisplay fuel=FuelDisplay.of(s.fuel,cfg.fuels.getOrDefault(Material.COAL,0.0));
        Snapshot next=new Snapshot(TemperatureModel.frame(s.temperature,cfg.ambient,cfg.maximum),(int)Math.round(s.temperature),status,ready,pack,fuel);
        int shownTemperature=displayTemperature(next.temperature);TitleSnapshot titleState=TitleSnapshot.of(next);
        if(!titleState.equals(m.titleSnapshot)){
            Snapshot titleView=new Snapshot(next.frame,shownTemperature,next.status,next.canWork,next.pack,next.fuel);
            p.getOpenInventory().setTitle(pack?title(titleView):"Кузнечный горн | "+shownTemperature+"°C | Топливо "+fuel.time());m.titleSnapshot=titleState;
        }
        ItemStack[] inputs=plugin.store.inputs(p.getUniqueId());
        for(int i=0;i<2;i++)set(m.inventory,10+2*i,inputs[i]);
        ItemStack reheating=inputs[2];
        Optional<Workpiece> reheatWorkpiece=plugin.items.readWorkpiece(reheating);
        if(reheatWorkpiece.isPresent()){
            Workpiece workpiece=reheatWorkpiece.get();
            double temperature=workpiece.at(System.currentTimeMillis(),cfg.ambient,cfg.metals.get(workpiece.metal()).cooling());
            int band=(int)Math.round(temperature/5.0);
            String sprite=ItemSprites.workpiece(workpiece,temperature,cfg.metals.get(workpiece.metal()).minimum());
            if(!Objects.equals(m.cachedReheatSource,reheating)||m.cachedReheatTemperature!=band||!Objects.equals(m.cachedReheatSprite,sprite)){
                m.cachedReheatSource=reheating.clone();m.cachedReheatDisplay=plugin.items.refreshLore(reheating);m.cachedReheatTemperature=band;m.cachedReheatSprite=sprite;
            }
            reheating=m.cachedReheatDisplay;
        }else{m.cachedReheatSource=null;m.cachedReheatDisplay=null;m.cachedReheatTemperature=Integer.MIN_VALUE;m.cachedReheatSprite=null;}
        set(m.inventory,14,reheating);
        setDynamic(m,16,pack+"|"+shownTemperature+"|"+cfg.working+"|"+cfg.maximum,()->button(pack?Material.PAPER:Material.FIRE_CHARGE,"Температура: "+shownTemperature+"°C",pack,"Рабочая: "+Math.round(cfg.working)+"°C","Максимум: "+Math.round(cfg.maximum)+"°C"));
        set(m.inventory,19,cachedButton(m,(pack?"pack":"vanilla")+"|load",Material.HOPPER,"Загрузить топливо",false,"ЛКМ: 1 предмет","Shift+ЛКМ: весь стак"));
        ItemStack reserve=button(pack?Material.PAPER:Material.COAL,fuel.seconds()>0?"Топливо горит — осталось "+fuel.time():"Топливо закончилось",pack,
                fuel.coalEquivalent()<0?"Уголь не настроен как топливо":"В пересчёте на уголь: ≈ "+fuel.coalEquivalent()+" шт.",
                "С учётом частично сгоревшего топлива",
                fuel.seconds()>0?"При "+Math.round(cfg.maximum)+"°C горн поддерживает жар":"Горн остывает без топлива");
        setDynamic(m,20,pack+"|"+fuel.seconds()+"|"+fuel.coalEquivalent(),()->reserve);
        // Empty slots beneath the fuel panel expose its tooltip without item icons.
        if(pack){set(m.inventory,33,reserve);set(m.inventory,34,reserve);set(m.inventory,35,reserve);}
        else {set(m.inventory,33,null);set(m.inventory,34,null);set(m.inventory,35,null);}
        setDynamic(m,21,pack+"|"+status,()->button(pack?Material.PAPER:Material.LIME_DYE,status.text,pack));
        set(m.inventory,23,cachedButton(m,(pack?"pack":"vanilla")+"|reheat",Material.BLAZE_POWDER,"Повторно нагреть",false,"ЛКМ — нагреть","Качество и этапы сохраняются"));
        set(m.inventory,25,cachedButton(m,(pack?"pack":"vanilla")+"|help",Material.BOOK,"Помощь",false,"ЛКМ — открыть"));
        set(m.inventory,53,cachedButton(m,(pack?"pack":"vanilla")+"|close",pack?Material.PAPER:Material.BARRIER,"Закрыть",pack,"ЛКМ — закрыть"));
        Metal metal=metal(inputs[1]);int amount=ForgeItems.empty(inputs[1])?0:inputs[1].getAmount();String recipeKey=pack+":"+metal+":"+amount+":"+ready;
        if(!recipeKey.equals(m.recipes)){m.recipes=recipeKey;for(Product product:Product.values()){int cost=cfg.recipes.get(product).ingots();set(m.inventory,product.slot,button(ForgeItems.icon(metal==null?Metal.IRON:metal,product),product.label,false,"ЛКМ — создать заготовку","Стоимость: "+cost+" слитков",ready&&metal!=null&&amount>=cost?"Можно создать":"Условия не выполнены"));}}
    }
    static String title(Snapshot s){
        StringBuilder b=new StringBuilder("§f").append(ForgeGuiGlyphRegistry.LEFT);layer(b,ForgeGuiGlyphRegistry.BASE);layer(b,ForgeGuiGlyphRegistry.LABELS);layer(b,(char)(ForgeGuiGlyphRegistry.HEAT+s.frame));
        String t=String.format("%4d",s.temperature);for(int i=0;i<4;i++)if(t.charAt(i)!=' ')layer(b,(char)(ForgeGuiGlyphRegistry.DIGITS+i*16+t.charAt(i)-'0'));
        layer(b,(char)0xE140);layer(b,(char)(ForgeGuiGlyphRegistry.STATUS+s.status.ordinal()));
        fuelDigits(b,s.fuel.clockDigits(),ForgeGuiGlyphRegistry.FUEL_TIME);
        fuelDigits(b,s.fuel.coalDigits(),ForgeGuiGlyphRegistry.FUEL_COAL);
        if(s.fuel.timeOverflow())layer(b,ForgeGuiGlyphRegistry.FUEL_OVERFLOW);
        return b.toString();
    }
    static void fuelDigits(StringBuilder b,String value,char base){for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c==' ')continue;int digit=c=='+'?10:c=='-'?11:c-'0';layer(b,(char)(base+i*16+digit));}}
    static void layer(StringBuilder b,char c){b.append(c).append(ForgeGuiGlyphRegistry.BACK);}
    static ItemStack button(Material type,String title,boolean invisible,String... lore){var i=new ItemStack(type);var meta=i.getItemMeta();meta.displayName(ForgeItems.text(title,NamedTextColor.GOLD));meta.lore(Arrays.stream(lore).map(s->ForgeItems.text(s,NamedTextColor.GRAY)).toList());if(invisible)meta.setItemModel(new NamespacedKey("servermine","empty"));i.setItemMeta(meta);return i;}
    ItemStack cachedButton(Menu menu,String key,Material type,String title,boolean invisible,String... lore){return menu.staticButtons.computeIfAbsent(key,k->button(type,title,invisible,lore));}
    void setDynamic(Menu menu,int slot,String key,java.util.function.Supplier<ItemStack> factory){if(!Objects.equals(menu.dynamicKeys.get(slot),key)){menu.dynamicKeys.put(slot,key);set(menu.inventory,slot,factory.get());}}
    static void set(Inventory inv,int n,ItemStack item){if(!Objects.equals(inv.getItem(n),item))inv.setItem(n,item);}
    void tick(){for(Menu m:new ArrayList<>(menus.values())){Player p=Bukkit.getPlayer(m.viewer);if(p==null)continue;if(!valid(m.core)||p.getWorld()!=m.core.getWorld()||p.getLocation().distanceSquared(m.core.getLocation())>64){feedback(m,Status.INVALID_STATION,2);render(p,m);p.closeInventory();}else render(p,m);}}
    void close(Player p,Menu m){if(m.closed)return;m.closed=true;menus.remove(p.getUniqueId(),m);plugin.safe(p,()->plugin.store.returnInputs(p));}
}
