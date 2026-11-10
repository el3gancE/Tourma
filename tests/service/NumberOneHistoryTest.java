package service;
import java.util.*;
import model.*;

public class NumberOneHistoryTest {
    static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    public static void main(String[] args) {
        List<PartnerParticipant> partners=List.of(
                new PartnerParticipant("b","s","Beta",null,null),
                new PartnerParticipant("a","s","Alpha",null,null));
        List<Tournament> tournaments=new ArrayList<>();
        for(int i=0;i<7;i++) {
            Tournament t=new Tournament();t.setId("t"+i);t.setName("Event "+i);
            t.setStatus(i==6?"INCOMING":"COMPLETED");t.setTournamentIndexInSeries(i+10);tournaments.add(t);
        }
        List<Map<String,Integer>> points=List.of(Map.of(),Map.of("alpha",10,"beta",10),Map.of(),
                Map.of("alpha",20),Map.of("beta",30),Map.of(),Map.of());
        var leaders=NumberOneHistory.build(tournaments,partners,points,2);
        check(leaders.size()==2,"Wrong unique leader count or zero-point leader included");
        var beta=leaders.get(0);var alpha=leaders.get(1);
        check(beta.getTeamName().equals("Beta"),"Tie must preserve official partner order");
        check(beta.getFirstIndex()==11 && beta.getLastIndex()==15,"Wrong historical event indices");
        check(beta.getMilestones()==4 && beta.getLongestStreak()==2,"Returning leader/streak mismatch");
        check(beta.isLatest() && !alpha.isLatest(),"Draft must not create a historical milestone");
        check(alpha.getMilestones()==1 && alpha.getLongestStreak()==1,"Leader change missing");
        check(beta.getReigns().size()==2,"A returning leader must start a separate reign");
        var first=beta.getReigns().get(0);var returning=beta.getReigns().get(1);
        check(first.getOrdinal()==1 && first.getFirstIndex()==11 && first.getLastIndex()==12 && first.getMilestones()==2,"First continuous range differs");
        check(alpha.getReigns().get(0).getOrdinal()==2,"Reigns must be numbered chronologically");
        check(returning.getOrdinal()==3 && returning.getFirstIndex()==14 && returning.getLastIndex()==15 && returning.isLatest() && !first.isLatest(),"Latest returning reign differs");
        check(NumberOneHistory.build(List.of(),partners,points,2).isEmpty(),"Empty Series should be empty");
        check(NumberOneHistory.build(tournaments,partners,List.of(),2).isEmpty(),"No points must not assign #1");
        // Independently compare incremental windows to full recomputation at every step.
        Random random=new Random(7);
        for(int run=0;run<100;run++) {
            int window=1+random.nextInt(6);
            List<Map<String,Integer>> matrix=new ArrayList<>();
            for(int i=0;i<7;i++)matrix.add(Map.of("alpha",random.nextInt(10),"beta",random.nextInt(10)));
            Map<String,Integer> expected=new LinkedHashMap<>();
            for(int step=0;step<6;step++) {
                String best=null;int bestPoints=0;
                for(PartnerParticipant partner:partners) {
                    String key=partner.getName().toLowerCase();int total=0;
                    for(int j=Math.max(0,step-window+1);j<=step;j++)total+=matrix.get(j).get(key);
                    if(total>bestPoints){bestPoints=total;best=key;}
                }
                if(best!=null)expected.merge(best,1,Integer::sum);
            }
            var actual=NumberOneHistory.build(tournaments,partners,matrix,window);
            check(actual.size()==expected.size(),"Random leader set mismatch");
            int i=0;for(var entry:expected.entrySet()) {
                var leader=actual.get(i++);
                check(leader.getTeamName().equalsIgnoreCase(entry.getKey()) && leader.getMilestones()==entry.getValue(),"Window expiry or ordering mismatch");
            }
        }
        System.out.println("PASS: ties, zero points, drafts, returning leaders, streaks, event indices and 100 window comparisons");
    }
}
