package ru.servermine.forge;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import ru.servermine.forge.Model.*;

final class ForgingService {
    static final class Active {
        final UUID player,item,session; final ForgeSession game; final Block anvil;
        String popup="";long popupUntil,lastStrike;
        Active(UUID p,UUID i,UUID s,ForgeSession game,Block anvil){player=p;item=i;session=s;this.game=game;this.anvil=anvil;}
    }
    final ForgeSystemPlugin plugin;
    final Map<UUID,Active> sessions=new HashMap<>();
    final Map<UUID,UUID> byItem=new HashMap<>();
    ForgingService(ForgeSystemPlugin plugin){this.plugin=plugin;}
    static boolean anvil(Material m){return m==Material.ANVIL||m==Material.CHIPPED_ANVIL||m==Material.DAMAGED_ANVIL;}
    boolean strike(Player p,Block block){
        if(!plugin.items.isHammer(p.getInventory().getItemInMainHand()))return false;
        var piece=plugin.items.readWorkpiece(p.getInventory().getItemInOffHand());if(piece.isEmpty())return false;
        Workpiece w=piece.get();if(w.state()!=State.HOT_BLANK&&w.state()!=State.FORGING)return false;
        if(!p.hasPermission("forgesystem.use"))return true;
        Active active=sessions.get(p.getUniqueId());long now=System.nanoTime();
        if(active==null){
            if(w.session()!=null||byItem.containsKey(w.id())){p.sendMessage("Заготовка занята другой сессией.");return true;}
            var r=plugin.settings.metals.get(w.metal());double t=w.at(System.currentTimeMillis(),plugin.settings.ambient,r.cooling());
            if(t<=r.minimum()){p.sendMessage("Заготовка остыла. Нагрейте её в горне.");return true;}
            UUID lock=UUID.randomUUID();ForgeSession game=new ForgeSession(plugin.settings.stages,ThreadLocalRandom.current(),w,t,r.minimum(),r.cooling(),now);
            plugin.store.transact(p,s->{s.contents[40]=plugin.items.change(s.contents[40],w.progress(State.FORGING,w.quality(),w.stage(),w.cap(),p.getUniqueId(),lock,t,System.currentTimeMillis()));});
            active=new Active(p.getUniqueId(),w.id(),lock,game,block);active.lastStrike=now;sessions.put(p.getUniqueId(),active);byItem.put(w.id(),p.getUniqueId());
            active.popup=w.cap()<3?"ЛИМИТ КАЧЕСТВА":"КОВКА";active.popupUntil=System.currentTimeMillis()+1100;show(p,active,now);
            p.playSound(p.getLocation(),Sound.BLOCK_ANVIL_LAND,.35f,1.35f);return true; // The first strike is never scored.
        }
        if(!active.item.equals(w.id())||!active.session.equals(w.session())||!active.anvil.equals(block))return true;
        if(now-active.lastStrike<150_000_000L)return true;active.lastStrike=now;
        ForgeSession.Outcome outcome=active.game.strike(now);
        if(outcome==ForgeSession.Outcome.COLD){stop(p,true);return true;}
        boolean finished=active.game.finished;
        Active a=active;plugin.store.transact(p,s->{var current=plugin.items.readWorkpiece(s.contents[40]).orElseThrow();s.contents[40]=plugin.items.change(s.contents[40],current.progress(finished?State.UNQUENCHED:State.FORGING,a.game.quality,a.game.stage,a.game.cap,p.getUniqueId(),finished?null:a.session,a.game.temperature,System.currentTimeMillis()));});
        active.popup=outcome==ForgeSession.Outcome.MISS?"ПРОМАХ":switch(active.game.quality){case NONE->"ПРОМАХ";case GOOD->"ХОРОШАЯ КОВКА";case EXCELLENT->"ОТЛИЧНАЯ КОВКА";case MASTERWORK->"МАСТЕРСКАЯ КОВКА";};active.popupUntil=System.currentTimeMillis()+1100;
        p.playSound(p.getLocation(),Sound.BLOCK_ANVIL_LAND,.8f,outcome==ForgeSession.Outcome.MISS?.65f:1.25f);show(p,active,now);
        if(finished){sessions.remove(p.getUniqueId());byItem.remove(active.item);Bukkit.getScheduler().runTaskLater(plugin,()->{if(p.isOnline()&&!sessions.containsKey(p.getUniqueId()))p.sendActionBar(Component.empty());},24);}
        return true;
    }
    void stop(Player p,boolean cold){
        Active a=sessions.get(p.getUniqueId());if(a==null)return;a.game.tick(System.nanoTime());
        plugin.store.transact(p,s->{for(int i=0;i<s.contents.length;i++){ItemStack item=s.contents[i];var read=plugin.items.readWorkpiece(item);if(read.isEmpty()||!read.get().id().equals(a.item))continue;
            Workpiece w=read.get();double actual=w.at(System.currentTimeMillis(),plugin.settings.ambient,a.game.cooling);
            int cap=a.game.cold?Math.min(a.game.cap,a.game.stage):a.game.cap;
            s.contents[i]=plugin.items.change(item,w.progress(State.FORGING,a.game.quality,a.game.stage,cap,p.getUniqueId(),null,actual,System.currentTimeMillis()));}});
        sessions.remove(p.getUniqueId());byItem.remove(a.item);
        p.sendActionBar(cold?Component.text("МЕТАЛЛ ОСТЫЛ — верните заготовку в горн",NamedTextColor.AQUA):Component.empty());
        if(cold){p.playSound(p.getLocation(),Sound.BLOCK_FIRE_EXTINGUISH,.65f,.8f);Bukkit.getScheduler().runTaskLater(plugin,()->{if(p.isOnline()&&!sessions.containsKey(p.getUniqueId()))p.sendActionBar(Component.empty());},16);}
    }
    void recoverLocks(Player p){plugin.store.transact(p,s->{for(int i=0;i<s.contents.length;i++){var read=plugin.items.readWorkpiece(s.contents[i]);if(read.isEmpty())continue;Workpiece w=read.get();if(w.session()!=null){var r=plugin.settings.metals.get(w.metal());double t=w.at(System.currentTimeMillis(),plugin.settings.ambient,r.cooling());s.contents[i]=plugin.items.change(s.contents[i],w.progress(State.FORGING,w.quality(),w.stage(),t<=r.minimum()?Math.min(w.cap(),w.stage()):w.cap(),w.smith(),null,t,System.currentTimeMillis()));}}});}
    void tick(long tick){
        for(Active a:new ArrayList<>(sessions.values())){Player p=Bukkit.getPlayer(a.player);if(p==null)continue;
            var off=plugin.items.readWorkpiece(p.getInventory().getItemInOffHand());
            if(p.isDead()||!plugin.items.isHammer(p.getInventory().getItemInMainHand())||off.isEmpty()||!off.get().id().equals(a.item)||p.getWorld()!=a.anvil.getWorld()||p.getLocation().distanceSquared(a.anvil.getLocation())>64||!anvil(a.anvil.getType())){plugin.safe(p,()->stop(p,false));continue;}
            a.game.tick(System.nanoTime());if(a.game.cold){plugin.safe(p,()->stop(p,true));continue;}if(tick%2==0)show(p,a,System.nanoTime());
        }
    }
    void show(Player p,Active a,long now){
        if(plugin.pack.loaded(p)){p.sendActionBar(HudRenderer.render(a.game,now,a.popupUntil>System.currentTimeMillis()?a.popup:""));return;}
        Component bar=Component.text("КОВКА "+a.game.stage+" | ",NamedTextColor.GOLD);int position=(int)Math.round(a.game.position(now)*30);
        for(int i=0;i<31;i++)bar=bar.append(Component.text(i==position?"◆":"━",i==position?NamedTextColor.WHITE:i/30.0>=a.game.zone&&i/30.0<=a.game.zone+a.game.width()?NamedTextColor.GREEN:NamedTextColor.DARK_GRAY));
        p.sendActionBar(bar.append(Component.text(" | "+Math.round(a.game.temperature)+"°C "+(a.popupUntil>System.currentTimeMillis()?a.popup:""),NamedTextColor.YELLOW)));
    }
    boolean quenchOrAssemble(Player p,Block block){
        var item=p.getInventory().getItemInMainHand();var read=plugin.items.readWorkpiece(item);if(read.isEmpty())return false;Workpiece w=read.get();
        if(w.state()==State.UNQUENCHED&&(block.getType()==Material.WATER||block.getType()==Material.WATER_CAULDRON)){
            plugin.store.transact(p,s->{int hand=p.getInventory().getHeldItemSlot();s.contents[hand]=plugin.items.change(s.contents[hand],w.progress(w.product().armor()?State.FINISHED:State.QUENCHED_PART,w.quality(),w.stage(),w.cap(),w.smith(),null,plugin.settings.ambient,System.currentTimeMillis()));});
            p.playSound(p.getLocation(),Sound.BLOCK_FIRE_EXTINGUISH,1,1);return true;
        }
        if(w.state()==State.QUENCHED_PART&&anvil(block.getType())){
            plugin.store.transact(p,s->{int sticks=plugin.settings.recipes.get(w.product()).sticks();var held=s.contents[40];if(ForgeItems.empty(held)||held.getType()!=Material.STICK||held.hasItemMeta()||held.getAmount()<sticks)throw new IllegalArgumentException("Возьмите в левую руку палки: "+sticks);
                ForgeMenuService.consume(s.contents,40,sticks);int hand=p.getInventory().getHeldItemSlot();s.contents[hand]=plugin.items.change(s.contents[hand],w.progress(State.FINISHED,w.quality(),w.stage(),w.cap(),w.smith(),null,w.temperature(),w.updated()));});return true;
        }
        return false;
    }
}
