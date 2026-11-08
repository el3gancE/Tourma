import dao.TournamentDAO;
import model.Tournament;
import java.util.*;
public class TournamentCombinedFilterCheck {
    static Set<String> ids(TournamentDAO d,String series,String q,String kind,String status) {
        Set<String> ids=new HashSet<>();
        var first=d.getTournamentPage(series,q,kind,status,"1");
        for(int page=1;page<=first.getTotalPages();page++)for(Tournament t:(page==1?first:d.getTournamentPage(series,q,kind,status,String.valueOf(page))).getTournaments())ids.add(t.getId());
        if(ids.size()!=first.getTotalCount())throw new AssertionError("Count/paging mismatch");
        return ids;
    }
    public static void main(String[] args) {
        TournamentDAO dao=new TournamentDAO();
        var all=dao.getTournamentPage(null,"","ALL","ALL","1");
        String series=all.getTournaments().stream().map(Tournament::getSeriesId).filter(s->s!=null&&!s.isBlank()).findFirst().orElse("");
        int checks=0;
        for(String q:List.of("","a")) {
            Map<String,Set<String>> kinds=new HashMap<>(),statuses=new HashMap<>();
            for(String kind:List.of("ALL","STANDALONE","SERIES"))kinds.put(kind,ids(dao,null,q,kind,"ALL"));
            for(String status:List.of("ALL","INCOMING","IN_PROGRESS","COMPLETED"))statuses.put(status,ids(dao,null,q,"ALL",status));
            for(String kind:kinds.keySet())for(String status:statuses.keySet()) {
                Set<String> expected=new HashSet<>(kinds.get(kind));expected.retainAll(statuses.get(status));
                if(!expected.equals(ids(dao,null,q,kind,status)))throw new AssertionError("Combination differs: "+kind+" "+status);
                checks++;
            }
            if(!series.isEmpty()) {
                Set<String> scoped=ids(dao,series,q,"SERIES","ALL");
                for(String status:statuses.keySet()) {
                    Set<String> expected=new HashSet<>(scoped);expected.retainAll(statuses.get(status));
                    if(!expected.equals(ids(dao,series,q,"SERIES",status)))throw new AssertionError("Series combination differs");
                    checks++;
                }
            }
        }
        if(!dao.getTournamentPage(series,"","STANDALONE","ALL","1").getSeriesId().isEmpty())throw new AssertionError("Standalone kept series");
        var legacy=dao.getTournamentPage(null,"","COMPLETED","1");
        if(!legacy.getStatus().equals("COMPLETED"))throw new AssertionError("Legacy link broke");
        System.out.println("PASS: "+checks+" combined filter/search intersections, counts, paging, Series scope, legacy links; read-only");
    }
}
