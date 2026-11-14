package shop.blinksmp.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DestinationPolicyTest {
    @Test void onlineAssignedWorldIsNotReportedAsMaintenance(){assertNull(DestinationPolicy.issue(true,true,false,"BlinkSMP-Overworld-02",true));}
    @Test void unassignedTerritoryIsDistinctFromOfflineBackend(){
        assertTrue(DestinationPolicy.issue(true,true,false,null,false).contains("outside assigned"));
        assertTrue(DestinationPolicy.issue(true,true,false,"BlinkSMP-Overworld-02",false).contains("maintenance"));
    }
    @Test void refreshAndRestartAreReportedAccurately(){
        assertTrue(DestinationPolicy.issue(false,true,false,"world",true).contains("refreshing"));
        assertTrue(DestinationPolicy.issue(true,false,false,"world",true).contains("refreshing"));
        assertTrue(DestinationPolicy.issue(true,true,true,"world",true).contains("restarting"));
    }
}
