package ru.servermine.forge;

import org.junit.jupiter.api.Test;
import java.util.*;
import ru.servermine.forge.Model.*;
import static org.junit.jupiter.api.Assertions.*;

class DomainTest {
    Workpiece blank(){return new Workpiece(UUID.randomUUID(),Metal.IRON,Product.SWORD,State.HOT_BLANK,Quality.NONE,0,1,3,null,null,1000,1000,"test");}
    ForgeSession.Rule[] rules(){return new ForgeSession.Rule[]{new ForgeSession.Rule(.34,1.8,.15,.03),new ForgeSession.Rule(.22,1.3,.18,.03),new ForgeSession.Rule(.12,.95,.20,.03)};}
    @Test void exactTemperatureBoundaries(){
        double[] temps={20,100,300,600,899,900,1000,1200};int[] frames={0,1,5,10,15,15,17,20};
        for(int i=0;i<temps.length;i++){assertEquals(frames[i],TemperatureModel.frame(temps[i],20,1200));assertEquals(temps[i]>=900,new TemperatureModel(temps[i],0,0).canWork(900));}
    }
    @Test void exhaustedFuelSplitsElapsedInterval(){var s=new TemperatureModel(20,10,0);s.update(20000,20,1200,25,8);assertEquals(190,s.temperature,.001);assertEquals(0,s.fuel);assertEquals(TemperatureModel.Status.COOLING,s.status(20,900));}
    @Test void coolingCanStillWork(){var s=new TemperatureModel(1000,0,0);s.update(10000,20,1200,25,8);assertEquals(920,s.temperature);assertTrue(s.canWork(900));assertEquals(TemperatureModel.Status.COOLING,s.status(20,900));}
    @Test void elapsedUpdateIsPartitionInvariant(){var a=new TemperatureModel(20,80,0);var b=new TemperatureModel(20,80,0);a.update(90000,20,1200,25,8);for(int t=1000;t<=90000;t+=1000)b.update(t,20,1200,25,8);assertEquals(a.temperature,b.temperature,.00001);assertEquals(a.fuel,b.fuel,.00001);}
    @Test void clampsExtremesAndBackwardsClock(){assertEquals(0,TemperatureModel.frame(-20,20,1200));assertEquals(20,TemperatureModel.frame(5000,20,1200));var s=new TemperatureModel(100,2,5000);s.update(0,20,1200,25,8);assertEquals(100,s.temperature);assertEquals(5000,s.updated);}
    @Test void persistentThermalAnchor(){assertEquals(780,blank().at(11000,20,22));assertEquals(20,blank().at(100000,20,22));assertEquals(1000,blank().at(0,20,22));}
    @Test void reheatPreservesAllProgress(){var w=blank().progress(State.FORGING,Quality.GOOD,2,2,UUID.randomUUID(),null,700,1000);var h=w.thermal(1000,2000);assertEquals(w.id(),h.id());assertEquals(w.quality(),h.quality());assertEquals(w.stage(),h.stage());assertEquals(w.cap(),h.cap());assertEquals(w.smith(),h.smith());assertEquals(w.revision(),h.revision());}
    @Test void reheatRejectsActiveAndFinished(){var w=blank();assertTrue(w.reheatAllowed());var active=w.progress(State.FORGING,Quality.NONE,1,3,UUID.randomUUID(),UUID.randomUUID(),900,1000);assertFalse(active.reheatAllowed());var u=active.progress(State.UNQUENCHED,Quality.GOOD,1,1,active.smith(),null,800,1000);assertFalse(u.reheatAllowed());}
    @Test void transitionGraph(){assertFalse(Model.transitionAllowed(State.HOT_BLANK,State.FINISHED,false));assertFalse(Model.transitionAllowed(State.FORGING,State.QUENCHED_PART,false));assertFalse(Model.transitionAllowed(State.UNQUENCHED,State.QUENCHED_PART,true));assertTrue(Model.transitionAllowed(State.UNQUENCHED,State.FINISHED,true));assertTrue(Model.transitionAllowed(State.QUENCHED_PART,State.FINISHED,false));}
    @Test void workpieceRejectsNonFiniteAndInconsistentProgress(){var w=blank();assertThrows(IllegalArgumentException.class,()->w.thermal(Double.NaN,1000));assertThrows(IllegalArgumentException.class,()->w.progress(State.FORGING,Quality.MASTERWORK,1,3,UUID.randomUUID(),null,800,1000));}
    @Test void threeHitsReachMasterwork(){var g=new ForgeSession(rules(),new Random(42),blank(),1000,700,.01,0);long time=0;for(int stage=1;stage<=3;stage++){double center=g.zone+g.width()/2;time+=(long)(center*rules()[stage-1].seconds()*1e9);var result=g.strike(time);assertEquals(stage==3?ForgeSession.Outcome.FINISHED:ForgeSession.Outcome.HIT,result);}assertEquals(Quality.MASTERWORK,g.quality);assertTrue(g.finished);}
    @Test void missOnFirstRepeatsWhileSecondPreservesGood(){var g=new ForgeSession(rules(),new Random(42),blank(),1000,700,.01,0);assertEquals(ForgeSession.Outcome.MISS,g.strike(0));long hit=(long)((g.zone+g.width()/2)*1.8*1e9);assertEquals(ForgeSession.Outcome.HIT,g.strike(hit));assertEquals(ForgeSession.Outcome.FINISHED,g.strike(hit));assertEquals(Quality.GOOD,g.quality);}
    @Test void coolingPermanentlyCapsAndResumeSeedsActualHeat(){var g=new ForgeSession(rules(),new Random(3),blank(),710,700,22,0);g.tick(1_000_000_000);assertTrue(g.cold);assertEquals(1,g.cap);var progress=blank().progress(State.FORGING,Quality.NONE,1,g.cap,UUID.randomUUID(),null,880,1000);var resumed=new ForgeSession(rules(),new Random(4),progress,880,700,22,0);assertEquals(880,resumed.temperature);assertEquals(1,resumed.cap);long hit=(long)((resumed.zone+resumed.width()/2)*1.8*1e9);assertEquals(ForgeSession.Outcome.FINISHED,resumed.strike(hit));assertEquals(Quality.GOOD,resumed.quality);}
    @Test void invalidDifficultyRejected(){assertThrows(IllegalArgumentException.class,()->new ForgeSession.Rule(.99,1,.2,.03));assertThrows(IllegalArgumentException.class,()->new ForgeSession.Rule(.2,Double.NaN,.2,.03));}
    @Test void geometryHasExactlyFiveToolsAndFourArmor(){assertArrayEquals(new int[]{28,29,30,31,32},Arrays.stream(Product.values()).filter(p->!p.armor()).mapToInt(p->p.slot).toArray());assertArrayEquals(new int[]{38,39,40,41},Arrays.stream(Product.values()).filter(Product::armor).mapToInt(p->p.slot).toArray());}
}
