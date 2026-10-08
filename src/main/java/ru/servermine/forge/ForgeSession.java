package ru.servermine.forge;

import java.util.random.RandomGenerator;
import ru.servermine.forge.Model.*;

/** Pure server-side reaction rules, including the v2.4 quality cap. */
public final class ForgeSession {
    public record Rule(double width,double seconds,double shift,double padding) {
        public Rule {
            if(!Double.isFinite(width)||!Double.isFinite(seconds)||!Double.isFinite(shift)||!Double.isFinite(padding)
                    ||width<=0||width>=1||seconds<=0||shift<0||shift>1||padding<0||padding>=0.5||width+2*padding>1)
                throw new IllegalArgumentException("Invalid stage rule");
        }
    }
    public enum Outcome { HIT, MISS, FINISHED, COLD }
    private final Rule[] rules;
    private final RandomGenerator random;
    public final double minimum, cooling;
    public int stage, cap;
    public Quality quality;
    public double temperature, zone;
    public boolean cold, finished;
    private long started, updated;
    private double previous=-1;
    public ForgeSession(Rule[] rules,RandomGenerator random,Workpiece piece,double actual,double minimum,double cooling,long now) {
        if(rules.length!=3||actual<=minimum||cooling<=0) throw new IllegalArgumentException("Invalid session");
        this.rules=rules.clone(); this.random=random; this.minimum=minimum; this.cooling=cooling;
        stage=piece.stage(); cap=piece.cap(); quality=piece.quality(); temperature=actual; started=updated=now; randomize();
    }
    public void tick(long now) {
        if(finished||cold||now<=updated) return;
        temperature-=cooling*(now-updated)/1_000_000_000.0; updated=now;
        if(temperature<=minimum) { temperature=minimum; cold=true; cap=Math.min(cap,stage); }
    }
    public double position(long now) { double phase=(Math.max(0,now-started)/1e9/rules[stage-1].seconds())%2; return phase<=1?phase:2-phase; }
    public double width() { return rules[stage-1].width(); }
    public Outcome strike(long now) {
        tick(now); if(cold) return Outcome.COLD; if(finished) return Outcome.FINISHED;
        double pos=position(now);
        if(pos<zone||pos>zone+width()) {
            if(stage==1) { started=now; randomize(); return Outcome.MISS; }
            finished=true; return Outcome.FINISHED;
        }
        quality=Quality.values()[stage];
        if(stage>=cap) { finished=true; return Outcome.FINISHED; }
        stage++; started=now; randomize(); return Outcome.HIT;
    }
    private void randomize() {
        Rule r=rules[stage-1];
        for(int i=0;i<16;i++) { zone=r.padding()+random.nextDouble()*(1-r.width()-2*r.padding());
            if(previous<0||Math.abs(zone+r.width()/2-previous)>=r.shift()) break; }
        previous=zone+r.width()/2;
    }
}
