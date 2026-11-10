package service;
import java.util.*;
import model.*;

public class NewNumberOneAnnouncementTest {
    static final List<PartnerParticipant> PARTNERS=List.of(
            new PartnerParticipant("a","s","Alpha",null,null),
            new PartnerParticipant("b","s","Beta",null,null));
    static List<Tournament> events(String... statuses) {
        List<Tournament> events=new ArrayList<>();
        for(int i=0;i<statuses.length;i++) {
            Tournament t=new Tournament();t.setId("t"+i);t.setName("Event "+i);
            t.setStatus(statuses[i]);t.setTournamentIndexInSeries(i+1);events.add(t);
        }
        return events;
    }
    static NumberOneHistory.Reign announcement(List<Tournament> events,List<Map<String,Integer>> points) {
        return NumberOneHistory.announcement(NumberOneHistory.build(events,PARTNERS,points,1));
    }
    static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    public static void main(String[] args) {
        List<Map<String,Integer>> points=List.of(Map.of("alpha",10),Map.of("beta",20),Map.of("beta",30),Map.of("alpha",40));
        check(announcement(events("COMPLETED"),points)==null,"Initial leader is not a change of #1");
        var open=events("COMPLETED","COMPLETED","IN_PROGRESS","INCOMING");
        var changed=announcement(open,points);
        check(changed!=null && changed.getTeamName().equals("Beta"),"New leader must announce until another event completes");
        check(announcement(open,points)!=null,"Repeated page opens must still announce");
        check(announcement(events("COMPLETED","COMPLETED","FINISHED"),points)==null,"Next completed event retaining #1 must end announcement");
        var next=announcement(events("COMPLETED","COMPLETED","COMPLETED","COMPLETED"),points);
        check(next!=null && next.getTeamName().equals("Alpha"),"Next change must announce the new leader");
        check(announcement(events("COMPLETED","COMPLETED","COMPLETED"),List.of(Map.of("alpha",10),Map.of(),Map.of("alpha",10)))==null,"Same player after a zero-point gap is not a change of owner");
        check(announcement(events("COMPLETED"),List.of(Map.of()))==null,"No points must not announce a leader");
        check(NumberOneHistory.announcement(null)==null,"Missing history must not announce");
        System.out.println("PASS: first leader, actual change, repeated opens, unfinished next event, next completion, new owner and zero-point gaps");
    }
}
