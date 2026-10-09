package ru.servermine.forge;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.servermine.forge.Model.*;

public final class ForgeSystemPlugin extends JavaPlugin implements Listener {
    Settings settings; DurableStore store; ForgeItems items; ForgeMenuService menus; ForgingService forging; PackService pack;
    private long tick;
    private final EquipmentRecipes equipmentRecipes=new EquipmentRecipes();
    @Override public void onEnable(){
        try {
            for(String f:List.of("config","materials","products","multiblocks","gui","messages","resourcepack"))if(!new java.io.File(getDataFolder(),f+".yml").exists())saveResource(f+".yml",false);
            settings=new Settings(getDataFolder());store=new DurableStore(getDataFolder().toPath().resolve("state.yml"));items=new ForgeItems(this);menus=new ForgeMenuService(this);forging=new ForgingService(this);pack=new PackService(this);
            try{pack.start();}catch(Exception e){getLogger().warning("Resource pack hosting unavailable; using vanilla fallback: "+e.getMessage());}
            getServer().getServicesManager().register(ForgeItemsApi.class,items,this,ServicePriority.Normal);
            getServer().getPluginManager().registerEvents(this,this);
            equipmentRecipes.remove();
            Objects.requireNonNull(getCommand("forge")).setExecutor(this);
            getCommand("forge").setTabCompleter((sender,command,alias,args)->args.length==1?List.of("open","hammer","kit","inspect","status","claim","reload","hot","selftest","migrate").stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList():List.of());
            getServer().getScheduler().runTaskTimer(this,()->{
                if(store.failed)return;tick++;
                try{forging.tick(tick);if(tick%settings.physics==0)menus.physics();if(tick%settings.visual==0)menus.tick();if(tick%settings.save==0)store.save();
                    if(tick%20==0)for(Player p:Bukkit.getOnlinePlayers())refreshLore(p);
                }catch(Exception ex){getLogger().log(java.util.logging.Level.SEVERE,"Forge scheduler error",ex);}
            },1,1);
            for(Player p:Bukkit.getOnlinePlayers())recover(p);
            getLogger().info("ForgeSystem 3.1.0 enabled: stations, ForgeItems, persistent forging, GUI v3.");
        }catch(Exception ex){getLogger().log(java.util.logging.Level.SEVERE,"ForgeSystem initialization failed",ex);getServer().getPluginManager().disablePlugin(this);}
    }
    @Override public void onDisable(){
        equipmentRecipes.restore();
        if(store!=null&&!store.failed&&forging!=null){for(Player p:Bukkit.getOnlinePlayers()){safe(p,()->forging.stop(p,false));if(menus.menus.containsKey(p.getUniqueId()))p.closeInventory();}try{menus.physics();store.save();}catch(Exception ex){getLogger().severe(ex.toString());}}
        if(pack!=null)pack.close();getServer().getServicesManager().unregisterAll(this);
    }
    void safe(Player p,Runnable operation){
        try{operation.run();}catch(IllegalArgumentException ex){p.sendMessage("§c"+ex.getMessage());}catch(Exception ex){p.sendMessage("§cОперация остановлена. Предметы сохраняются в журнале восстановления.");getLogger().log(java.util.logging.Level.SEVERE,"Forge operation failed for "+p.getUniqueId(),ex);}
    }
    void recover(Player p){safe(p,()->{store.recover(p);forging.recoverLocks(p);store.returnInputs(p);store.claim(p);});Bukkit.getScheduler().runTaskLater(this,()->{if(p.isOnline())pack.send(p);},30);}
    void refreshLore(Player p){
        refreshInventorySprites(p.getInventory());
        ItemStack cursor=p.getItemOnCursor();if(items.isHammer(cursor)||items.readWorkpiece(cursor).isPresent()){ItemStack after=items.refreshLore(cursor);if(!Objects.equals(cursor,after))p.setItemOnCursor(after);}
        Inventory top=p.getOpenInventory().getTopInventory();
        if(!(top.getHolder() instanceof ForgeMenuService.Menu))refreshInventorySprites(top);
    }
    void refreshInventorySprites(Inventory inventory){for(int i=0;i<inventory.getSize();i++){ItemStack before=inventory.getItem(i);if(items.isHammer(before)||items.readWorkpiece(before).isPresent()){ItemStack after=items.refreshLore(before);if(!Objects.equals(before,after))inventory.setItem(i,after);}}}
    @EventHandler public void join(PlayerJoinEvent e){recover(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){Player p=e.getPlayer();safe(p,()->forging.stop(p,false));if(menus.menus.containsKey(p.getUniqueId()))p.closeInventory();pack.quit(p);menus.operations.remove(p.getUniqueId());}
    @EventHandler public void resource(PlayerResourcePackStatusEvent e){pack.status(e);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND)return;Player p=e.getPlayer();Block block=e.getClickedBlock();
        if(e.getAction()!=Action.RIGHT_CLICK_BLOCK&&e.getAction()!=Action.RIGHT_CLICK_AIR)return;
        if(block!=null&&ForgingService.anvil(block.getType())&&items.isHammer(p.getInventory().getItemInMainHand())){
            e.setCancelled(true);safe(p,()->forging.strike(p,block));return;
        }
        if(block!=null&&ForgingService.anvil(block.getType())&&items.readWorkpiece(p.getInventory().getItemInMainHand()).map(w->w.state()==State.FINISHED).orElse(false)){
            e.setCancelled(true);safe(p,()->forging.upgradeDiamond(p,block));return;
        }
        if(block!=null&&block.getType()==Material.BLAST_FURNACE&&items.isHammer(p.getInventory().getItemInMainHand())){e.setCancelled(true);safe(p,()->menus.open(p,block));return;}
        Block target=block!=null&&block.getType()==Material.WATER_CAULDRON?block:p.getTargetBlockExact(5,FluidCollisionMode.ALWAYS);
        if(target!=null)safe(p,()->{if(forging.quenchOrAssemble(p,target))e.setCancelled(true);});
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(items.locked(e.getCurrentItem())||items.locked(e.getCursor())||(e.getHotbarButton()>=0&&items.locked(p.getInventory().getItem(e.getHotbarButton())))||(e.getClick()==ClickType.SWAP_OFFHAND&&items.locked(p.getInventory().getItemInOffHand()))){e.setCancelled(true);return;}
        if(e.getView().getTopInventory().getHolder() instanceof ForgeMenuService.Menu m){menus.click(e,p,m);return;}
        InventoryType type=e.getView().getTopInventory().getType();
        boolean process=!Set.of(InventoryType.CHEST,InventoryType.ENDER_CHEST,InventoryType.BARREL,InventoryType.SHULKER_BOX,InventoryType.HOPPER,InventoryType.DROPPER,InventoryType.DISPENSER,InventoryType.PLAYER).contains(type);
        if(process&&(items.technical(e.getCurrentItem())||items.technical(e.getCursor())||(e.getHotbarButton()>=0&&items.technical(p.getInventory().getItem(e.getHotbarButton()))))){
            boolean touchesTop=e.getRawSlot()<e.getView().getTopInventory().getSize()||e.isShiftClick()||e.getClick()==ClickType.DOUBLE_CLICK;
            if(touchesTop)e.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drag(InventoryDragEvent e){
        if(items.locked(e.getOldCursor())){e.setCancelled(true);return;}
        boolean top=e.getRawSlots().stream().anyMatch(i->i<e.getView().getTopInventory().getSize());
        if(top&&(e.getView().getTopInventory().getHolder() instanceof ForgeMenuService.Menu||items.technical(e.getOldCursor())))e.setCancelled(true);
    }
    @EventHandler public void close(InventoryCloseEvent e){if(e.getInventory().getHolder() instanceof ForgeMenuService.Menu m&&e.getPlayer() instanceof Player p)menus.close(p,m);}
    @EventHandler(ignoreCancelled=true) public void drop(PlayerDropItemEvent e){if(items.locked(e.getItemDrop().getItemStack()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void swap(PlayerSwapHandItemsEvent e){if(items.locked(e.getMainHandItem())||items.locked(e.getOffHandItem()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void move(InventoryMoveItemEvent e){if(items.technical(e.getItem())&&e.getDestination().getType()!=InventoryType.CHEST&&e.getDestination().getType()!=InventoryType.BARREL&&e.getDestination().getType()!=InventoryType.HOPPER)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void craft(PrepareItemCraftEvent e){
        ItemStack result=e.getInventory().getResult();
        if(!ForgeItems.empty(result)&&EquipmentRules.blockedCraft(result.getType())){e.getInventory().setResult(null);return;}
        for(var i:e.getInventory().getMatrix())if(items.technical(i)){e.getInventory().setResult(null);break;}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void craftResult(CraftItemEvent e){
        if(EquipmentRules.blockedCraft(e.getRecipe().getResult().getType())||(!ForgeItems.empty(e.getCurrentItem())&&EquipmentRules.blockedCraft(e.getCurrentItem().getType())))e.setCancelled(true);
        for(var item:e.getInventory().getMatrix())if(items.technical(item))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void autoCraft(org.bukkit.event.block.CrafterCraftEvent e){
        if(EquipmentRules.blockedCraft(e.getResult().getType())||EquipmentRules.blockedCraft(e.getRecipe().getResult().getType()))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void furnace(FurnaceSmeltEvent e){if(items.technical(e.getSource()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void burn(FurnaceBurnEvent e){if(items.technical(e.getFuel()))e.setCancelled(true);}
    @EventHandler public void anvil(PrepareAnvilEvent e){for(var i:e.getInventory().getContents())if(items.technical(i)){e.setResult(null);break;}}
    @EventHandler public void smithing(PrepareSmithingEvent e){for(var i:e.getInventory().getContents())if(items.technical(i)){e.setResult(null);break;}}
    @EventHandler public void grind(PrepareGrindstoneEvent e){for(var i:e.getInventory().getContents())if(items.technical(i)){e.setResult(null);break;}}
    @EventHandler public void death(PlayerDeathEvent e){
        Player p=e.getEntity();
        // Bukkit already prepared the death drops. Keep GUI escrow out of that inventory.
        var menu=menus.menus.remove(p.getUniqueId());
        if(menu!=null){menu.closed=true;menu.inventory.setItem(10,null);menu.inventory.setItem(12,null);menu.inventory.setItem(14,null);}
        safe(p,()->forging.stop(p,false));
        for(int i=0;i<e.getDrops().size();i++){var old=items.readWorkpiece(e.getDrops().get(i));if(old.isEmpty()||old.get().session()==null)continue;Workpiece w=old.get();
            var r=settings.metals.get(w.metal());double t=w.at(System.currentTimeMillis(),settings.ambient,r.cooling());
            e.getDrops().set(i,items.change(e.getDrops().get(i),w.progress(State.FORGING,w.quality(),w.stage(),t<=r.minimum()?Math.min(w.cap(),w.stage()):w.cap(),w.smith(),null,t,System.currentTimeMillis())));
        }
    }
    @EventHandler public void respawn(PlayerRespawnEvent e){Bukkit.getScheduler().runTask(this,()->safe(e.getPlayer(),()->{store.returnInputs(e.getPlayer());store.claim(e.getPlayer());}));}
    @EventHandler(ignoreCancelled=true) public void damage(EntityDamageByEntityEvent e){if(e.getDamager() instanceof Player p&&items.isHammer(p.getInventory().getItemInMainHand()))e.setCancelled(true);}
    void help(CommandSender p){p.sendMessage("§6Кузница: плавильная печь на кирпичном блоке.");p.sendMessage("§eПКМ молотом по печи → топливо → металл → изделие.");p.sendMessage("§eЗаготовка в левой руке, молот в правой: ПКМ по наковальне.");p.sendMessage("§eПервый удар запускает ковку. Остывшую заготовку верните в горн.");p.sendMessage("§eДеталь в правой руке: ПКМ по воде; затем для инструментов ПКМ по наковальне с палками в левой руке.");p.sendMessage("§bГотовое кованое железное снаряжение в правую руку, алмазы в левую: ПКМ по наковальне — улучшить.");p.sendMessage("§e/forge claim — забрать предметы, которым не хватило места.");}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        String action=args.length==0?"open":args[0].toLowerCase(Locale.ROOT);
        if(action.equals("help")){help(sender);return true;}
        if(action.equals("status")||action.equals("info")){sender.sendMessage("ForgeSystem 3.1.0 | stations="+(store.data.getConfigurationSection("stations")==null?0:store.data.getConfigurationSection("stations").getKeys(false).size())+" | sessions="+forging.sessions.size()+" | storage="+(store.failed?"FAILED":"OK"));return true;}
        if(!Set.of("open","claim","stop","inspect").contains(action)&&!sender.hasPermission("forgesystem.admin")){sender.sendMessage("Нет права forgesystem.admin.");return true;}
        if(action.equals("selftest")){try{selftest();InteractionSelfTest.run(this);EquipmentSelfTest.run(this);StorageSelfTest.run(getDataFolder().toPath());sender.sendMessage("ForgeSystem SELFTEST PASS: diamond upgrades, recipe restrictions, right-click routing, codec, sprite switching, hammer migration, vanilla finished items, thermal anchor, locks, transitions, escrow, rollback, crash replay, full-inventory recovery.");}catch(Exception ex){sender.sendMessage("SELFTEST FAIL: "+ex);getLogger().log(java.util.logging.Level.SEVERE,"Selftest",ex);}return true;}
        if(action.equals("reload")){try{Settings next=new Settings(getDataFolder());settings=next;sender.sendMessage("Конфигурация проверена и обновлена. Настройки HTTP применятся после перезапуска.");}catch(Exception ex){sender.sendMessage("Конфигурация отклонена: "+ex.getMessage());}return true;}
        if(!(sender instanceof Player p)){sender.sendMessage("Эта команда предназначена для игрока.");return true;}
        safe(p,()->{switch(action){
            case "open" -> {Block core=p.getTargetBlockExact(6);if(core==null||core.getType()!=Material.BLAST_FURNACE)p.sendMessage("Смотрите на плавильную печь. Для открытия используйте кузнечный молот.");else if(!items.isHammer(p.getInventory().getItemInMainHand()))p.sendMessage("Возьмите кузнечный молот в правую руку.");else menus.open(p,core);}
            case "hammer" -> store.transact(p,s->{if(!s.add(items.createHammer()))throw new IllegalArgumentException(settings.message("full-inventory"));});
            case "kit" -> store.transact(p,s->{for(ItemStack i:List.of(items.createHammer(),new ItemStack(Material.COAL,16),new ItemStack(Material.IRON_INGOT,32),new ItemStack(Material.GOLD_INGOT,32),new ItemStack(Material.COPPER_INGOT,32)))if(!s.add(i))throw new IllegalArgumentException(settings.message("full-inventory"));});
            case "claim" -> {store.claim(p);p.sendMessage("Предметы возвращены по мере наличия места.");}
            case "stop" -> forging.stop(p,false);
            case "inspect" -> p.sendMessage(items.readWorkpiece(p.getInventory().getItemInMainHand()).map(Object::toString).orElse(items.isHammer(p.getInventory().getItemInMainHand())?"Forge Hammer schema 1":"Нет валидного кузнечного предмета"));
            case "hot" -> {var m=menus.menus.get(p.getUniqueId());if(m==null)throw new IllegalArgumentException("Сначала откройте горн.");store.transact(p,s->{var station=menus.state(m.station);station.temperature=Math.min(settings.maximum,settings.working+100);menus.write(m.station,station);});menus.render(p,m);}
            case "migrate" -> store.transact(p,s->{int hand=p.getInventory().getHeldItemSlot();s.contents[hand]=items.migrate(s.contents[hand]);});
            default -> help(p);
        }});return true;
    }
    private void selftest(){
        if(menus.metal(new ItemStack(Material.COPPER_INGOT))!=Metal.COPPER||!menus.allowedInput(1,new ItemStack(Material.COPPER_INGOT,64)))throw new IllegalStateException("Copper input rejected");
        if(menus.metal(new ItemStack(Material.RAW_COPPER))!=null)throw new IllegalStateException("Raw copper accepted as ingot");
        Set<String> definitions=new HashSet<>();
        try(var zip=new java.util.zip.ZipInputStream(Objects.requireNonNull(getResource("forge.zip")))){
            java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null)definitions.add(entry.getName());
        }catch(java.io.IOException ex){throw new IllegalStateException("Cannot inspect embedded sprites",ex);}
        for(Metal m:Metal.values())for(Product product:Product.values()){
            ItemStack item=items.createWorkpiece(m,product,950);Workpiece w=items.readWorkpiece(item).orElseThrow();
            for(double temperature:new double[]{20,350,1000}){
                ItemStack visual=items.updateThermalAnchor(item,temperature,System.currentTimeMillis());
                Workpiece decoded=items.readWorkpiece(visual).orElseThrow();
                var model=visual.getItemMeta().getItemModel();String heat=temperature<300?"cold":temperature<600?"warm":"hot";
                if(model==null||!model.getKey().endsWith("/"+heat)||!definitions.contains("assets/servermine/items/"+model.getKey()+".json"))throw new IllegalStateException("Missing or wrong sprite: "+m+"/"+product+"/"+heat);
                if(!w.id().equals(decoded.id())||w.quality()!=decoded.quality()||w.stage()!=decoded.stage()||w.cap()!=decoded.cap())throw new IllegalStateException("Sprite changed item identity or progress");
            }
            var legacy=item.clone();var legacyMeta=legacy.getItemMeta();legacyMeta.setItemModel(null);legacy.setItemMeta(legacyMeta);
            var refreshed=items.refreshLore(legacy);
            if(refreshed.getItemMeta().getItemModel()==null||!items.readWorkpiece(refreshed).orElseThrow().id().equals(w.id()))throw new IllegalStateException("Existing workpiece appearance update failed");
            Workpiece heated=items.readWorkpiece(items.updateThermalAnchor(item,1000,System.currentTimeMillis())).orElseThrow();
            if(!heated.id().equals(w.id())||heated.stage()!=w.stage()||heated.cap()!=w.cap())throw new AssertionError("reheat changed identity");
            Workpiece active=w.progress(State.FORGING,Quality.NONE,1,3,UUID.randomUUID(),UUID.randomUUID(),900,System.currentTimeMillis());var locked=items.change(item,active);
            if(items.readWorkpiece(locked).orElseThrow().reheatAllowed())throw new AssertionError("active lock");
            var finished=active.progress(State.UNQUENCHED,Quality.GOOD,1,1,active.smith(),null,800,System.currentTimeMillis());var unquenched=items.change(locked,finished);
            if(items.readWorkpiece(unquenched).orElseThrow().reheatAllowed())throw new AssertionError("unquenched reheat");
            var cooled=finished.progress(product.armor()?State.FINISHED:State.QUENCHED_PART,Quality.GOOD,1,1,finished.smith(),null,20,System.currentTimeMillis());
            var quenched=items.change(unquenched,cooled);
            var finalItem=product.armor()?quenched:items.change(quenched,cooled.progress(State.FINISHED,Quality.GOOD,1,1,cooled.smith(),null,20,System.currentTimeMillis()));
            if(finalItem.getItemMeta().hasItemModel()||finalItem.getType()!=ForgeItems.icon(m,product)||!items.readWorkpiece(finalItem).orElseThrow().id().equals(w.id()))throw new IllegalStateException("Finished item must retain vanilla appearance and identity");
            try{items.change(item,w.progress(State.FINISHED,Quality.GOOD,1,1,UUID.randomUUID(),null,20,System.currentTimeMillis()));throw new AssertionError("invalid transition");}catch(IllegalArgumentException expected){}
            ItemStack stacked=item.clone();stacked.setAmount(2);if(items.readWorkpiece(stacked).isPresent())throw new AssertionError("stack accepted");
        }
        var hammer=items.createHammer();
        if(!items.isHammer(hammer)||!ForgeItems.HAMMER_MODEL.equals(hammer.getItemMeta().getItemModel())||!definitions.contains("assets/servermine/items/forge/hammer.json"))throw new IllegalStateException("Hammer sprite missing");
        var legacyHammer=hammer.clone();var oldMeta=legacyHammer.getItemMeta();oldMeta.setItemModel(null);
        oldMeta.displayName(net.kyori.adventure.text.Component.text("Именной молот"));legacyHammer.setItemMeta(oldMeta);
        var refreshedHammer=items.refreshLore(legacyHammer);
        if(!items.isHammer(refreshedHammer)||!ForgeItems.HAMMER_MODEL.equals(refreshedHammer.getItemMeta().getItemModel())||legacyHammer.getItemMeta().hasItemModel())throw new IllegalStateException("Hammer migration failed");
        var restored=refreshedHammer.clone();var restoredMeta=restored.getItemMeta();restoredMeta.setItemModel(null);restored.setItemMeta(restoredMeta);
        if(!restored.equals(legacyHammer))throw new IllegalStateException("Hammer migration changed metadata");
        var vanillaAxe=new ItemStack(Material.IRON_AXE);
        if(!vanillaAxe.equals(items.refreshLore(vanillaAxe)))throw new IllegalStateException("Vanilla axe changed");
    }
}
