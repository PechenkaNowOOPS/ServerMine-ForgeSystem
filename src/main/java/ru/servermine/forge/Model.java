package ru.servermine.forge;

import java.util.UUID;

public final class Model {
    private Model() {}
    public enum State { HOT_BLANK, FORGING, UNQUENCHED, QUENCHED_PART, FINISHED }
    public enum Quality { NONE, GOOD, EXCELLENT, MASTERWORK }
    public enum Metal { IRON, GOLD, COPPER }
    public enum Product {
        SWORD(28, "Меч"), PICKAXE(29, "Кирка"), AXE(30, "Топор"), SHOVEL(31, "Лопата"), HOE(32, "Мотыга"),
        HELMET(38, "Шлем"), CHESTPLATE(39, "Нагрудник"), LEGGINGS(40, "Поножи"), BOOTS(41, "Ботинки");
        public final int slot; public final String label;
        Product(int slot, String label) { this.slot=slot; this.label=label; }
        public boolean armor() { return ordinal()>=5; }
        public static Product at(int slot) { for(var p:values()) if(p.slot==slot) return p; return null; }
    }
    public record Workpiece(UUID id, Metal metal, Product product, State state, Quality quality,
                            int qualityStage, int stage, int cap, UUID smith, UUID session,
                            double temperature, long updated, String revision) {
        public Workpiece {
            if(id==null||metal==null||product==null||state==null||quality==null||revision==null||revision.isBlank()
                    ||!Double.isFinite(temperature)||temperature<0||updated<0||stage<1||stage>3||cap<stage||cap>3
                    ||qualityStage!=quality.ordinal()||qualityStage>cap||qualityStage>stage
                    ||(session!=null&&(state!=State.FORGING||smith==null))
                    ||(state==State.HOT_BLANK&&(quality!=Quality.NONE||stage!=1||session!=null||smith!=null))
                    ||(state==State.FORGING&&(qualityStage!=stage-1||smith==null))
                    ||((state==State.UNQUENCHED||state==State.QUENCHED_PART||state==State.FINISHED)&&(qualityStage==0||smith==null))
                    ||(product.armor()&&state==State.QUENCHED_PART)) throw new IllegalArgumentException("Invalid Workpiece");
        }
        public boolean reheatAllowed() { return session==null&&(state==State.HOT_BLANK||state==State.FORGING); }
        public double at(long now, double ambient, double rate) {
            return Math.max(ambient,temperature-Math.max(0,now-updated)/1000.0*rate);
        }
        public Workpiece thermal(double t,long now) { return new Workpiece(id,metal,product,state,quality,qualityStage,stage,cap,smith,session,t,now,revision); }
        public Workpiece progress(State s,Quality q,int n,int c,UUID owner,UUID lock,double t,long now) {
            return new Workpiece(id,metal,product,s,q,q.ordinal(),n,c,owner,lock,t,now,revision);
        }
    }
    public static boolean transitionAllowed(State from,State to,boolean armor) {
        return switch(from) {
            case HOT_BLANK -> to==State.HOT_BLANK||to==State.FORGING;
            case FORGING -> to==State.FORGING||to==State.UNQUENCHED;
            case UNQUENCHED -> to==(armor?State.FINISHED:State.QUENCHED_PART);
            case QUENCHED_PART -> !armor&&to==State.FINISHED;
            case FINISHED -> to==State.FINISHED;
        };
    }
}
