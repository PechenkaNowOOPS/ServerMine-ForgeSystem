package ru.servermine.forge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FuelDisplayTest {
    @Test void positiveRemainderNeverLooksEmpty() {
        var last = FuelDisplay.of(.1, 80);
        assertEquals("00:00:01", last.time());
        assertEquals("   1", last.coalDigits());
        assertEquals("00:00:00", FuelDisplay.of(0, 80).time());
        assertEquals("   0", FuelDisplay.of(0, 80).coalDigits());
    }

    @Test void equivalentIncludesPartlyBurnedCoalAndOtherFuel() {
        assertEquals(18, FuelDisplay.of(1424.17, 80).coalEquivalent());
        assertEquals("00:23:45", FuelDisplay.of(1424.17, 80).time());
        assertEquals(10, FuelDisplay.of(800, 80).coalEquivalent());
        assertEquals("----", FuelDisplay.of(80, 0).coalDigits());
    }

    @Test void timerChangesTitleWhileTemperatureIsAtMaximum() {
        var station = new TemperatureModel(1200, 1440, 0);
        var before = snapshot(station);
        station.update(1000, 20, 1200, 25, 8);
        var after = snapshot(station);
        assertEquals(before.temperature(), after.temperature());
        assertEquals(before.frame(), after.frame());
        assertNotEquals(before, after);
        assertNotEquals(ForgeMenuService.title(before), ForgeMenuService.title(after));
        assertEquals("002359", after.fuel().clockDigits());
    }

    @Test void titleTemperatureIsQuantizedWithoutDroppingThermometerFrames() {
        var a=new ForgeMenuService.Snapshot(10,500,ForgeMenuService.Status.HEATING,true,true,FuelDisplay.of(80,80));
        var b=new ForgeMenuService.Snapshot(10,511,ForgeMenuService.Status.HEATING,true,true,FuelDisplay.of(80,80));
        var c=new ForgeMenuService.Snapshot(10,513,ForgeMenuService.Status.HEATING,true,true,FuelDisplay.of(80,80));
        assertEquals(500,ForgeMenuService.displayTemperature(a.temperature()));
        assertEquals(500,ForgeMenuService.displayTemperature(b.temperature()));
        assertEquals(525,ForgeMenuService.displayTemperature(c.temperature()));
        assertEquals(ForgeMenuService.TitleSnapshot.of(a),ForgeMenuService.TitleSnapshot.of(b));
        assertNotEquals(ForgeMenuService.TitleSnapshot.of(b),ForgeMenuService.TitleSnapshot.of(c));
        var nextFrame=new ForgeMenuService.Snapshot(11,511,ForgeMenuService.Status.HEATING,true,true,FuelDisplay.of(80,80));
        assertNotEquals(ForgeMenuService.TitleSnapshot.of(b),ForgeMenuService.TitleSnapshot.of(nextFrame));
        assertTrue(TemperatureModel.frame(500,20,1200)>=0&&TemperatureModel.frame(500,20,1200)<=20);
    }

    @Test void titleSnapshotSimulationReducesRefreshesComparedWithEveryTemperatureChange() {
        ForgeMenuService.Snapshot previous=null;ForgeMenuService.TitleSnapshot previousTitle=null;
        int oldTitleUpdates=0,policyTitleUpdates=0;
        for(int tick=0;tick<=400;tick+=5){
            int temperature=(int)Math.round(20+Math.min(500,tick*25.0/20));
            var current=new ForgeMenuService.Snapshot(TemperatureModel.frame(temperature,20,1200),temperature,
                    ForgeMenuService.Status.HEATING,false,true,FuelDisplay.of(Math.max(0,600-tick/20.0),80));
            if(!current.equals(previous))oldTitleUpdates++;
            var title=ForgeMenuService.TitleSnapshot.of(current);
            if(!title.equals(previousTitle))policyTitleUpdates++;
            previous=current;previousTitle=title;
        }
        assertTrue(policyTitleUpdates*5<=oldTitleUpdates*3,
                "title refreshes: baseline="+oldTitleUpdates+", policy="+policyTitleUpdates);
    }

    @Test void largeReservesHaveExplicitOverflowAndExactTooltipTime() {
        var display = FuelDisplay.of(800001, 80);
        assertTrue(display.timeOverflow());
        assertEquals("995959", display.clockDigits());
        assertEquals("222:13:21", display.time());
        assertEquals("999+", display.coalDigits());
        assertThrows(IllegalArgumentException.class, () -> FuelDisplay.of(Double.NaN, 80));
    }

    private ForgeMenuService.Snapshot snapshot(TemperatureModel s) {
        return new ForgeMenuService.Snapshot(20, 1200, ForgeMenuService.Status.WORKING, true, true, FuelDisplay.of(s.fuel, 80));
    }
}
