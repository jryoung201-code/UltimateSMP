package shop.ultimatesmp.weather;
/** Dimension-local state; Nether/End never overwrite Overworld. */
public record Snapshot(String dimension,long fullTime,boolean daylight,boolean weatherCycle,boolean storm,boolean thunder,int rainTicks,int thunderTicks,int clearTicks,long ageMillis){
 public Snapshot{if(!java.util.Set.of("Overworld","Nether","End").contains(dimension)||fullTime<0||fullTime>Long.MAX_VALUE-1_000_000||rainTicks<0||thunderTicks<0||clearTicks<0||ageMillis<0)throw new IllegalArgumentException("Invalid world snapshot");}
 public long elapsed(){return Math.min(300,ageMillis/50);}
 public long predictedTime(){return fullTime+(daylight?elapsed():0);}
 public int remaining(int ticks){return (int)Math.max(0,ticks-elapsed());}
 public boolean fresh(){return ageMillis<=15000;}
}
