package ru.servermine.forge;

public final class TemperatureModel {
    public enum Status { COLD, HEATING, WORKING, COOLING }
    public double temperature, fuel;
    public long updated;
    public TemperatureModel(double t,double fuel,long updated) { if(!Double.isFinite(t)||!Double.isFinite(fuel)||t<0||fuel<0||updated<0)throw new IllegalArgumentException("Invalid thermal state");this.temperature=t; this.fuel=fuel; this.updated=updated; }
    public void update(long now,double ambient,double maximum,double heat,double cool) {
        if(now<=updated) return;
        double elapsed=(now-updated)/1000.0, burning=Math.min(elapsed,fuel);
        temperature=Math.min(maximum,temperature+heat*burning);
        temperature=Math.max(ambient,temperature-cool*(elapsed-burning));
        fuel=Math.max(0,fuel-burning); updated=now;
    }
    public boolean canWork(double working) { return temperature>=working; }
    public Status status(double ambient,double working) {
        if(fuel>0) return canWork(working)?Status.WORKING:Status.HEATING;
        return temperature>ambient?Status.COOLING:Status.COLD;
    }
    public static int frame(double t,double ambient,double maximum) {
        return (int)Math.round(Math.clamp((t-ambient)/(maximum-ambient),0.0,1.0)*20);
    }
}
