package ru.servermine.forge;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

final class HudRenderer {
    static Component render(ForgeSession s,long now,String popup){
        StringBuilder b=new StringBuilder();layer(b,0xE000);layer(b,0xE001+s.stage-1);
        int width=(int)Math.round((Math.clamp(s.width(),.04,.60)-.04)/.02);
        int position=(int)Math.round(Math.clamp(s.zone+s.width()/2,0,1)*30);
        layer(b,0xE200+width*31+position);layer(b,0xE010+(int)Math.round(s.position(now)*30));
        layer(b,0xE040+Math.clamp((int)Math.round((s.temperature-s.minimum)/(1200-s.minimum)*10),0,10));
        layer(b,0xE050+s.quality.ordinal());
        int status=s.finished?4:s.cold?2:s.cap<3?3:0;layer(b,0xE060+status);
        int pop=switch(popup){case "ХОРОШАЯ КОВКА"->0;case "ОТЛИЧНАЯ КОВКА"->1;case "МАСТЕРСКАЯ КОВКА"->2;case "ПРОМАХ"->3;case "МЕТАЛЛ ОСТЫЛ"->4;case "ЛИМИТ КАЧЕСТВА"->5;default->-1;};if(pop>=0)layer(b,0xE070+pop);
        b.append('\uE7F1'); // Restore the actual 193px width for ActionBar centering.
        return Component.text(b.toString(),NamedTextColor.WHITE).font(Key.key("servermine:forge_hud"));
    }
    static void layer(StringBuilder b,int code){b.append((char)code).append('\uE7F0');}
}
