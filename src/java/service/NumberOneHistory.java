package service;

import java.util.*;
import model.PartnerParticipant;
import model.Tournament;

/** Projects historical #1s from the same ordered point matrix as Rolling standings. */
public final class NumberOneHistory {
    public static final class Reign {
        private final PartnerParticipant partner;
        private final Tournament first;
        private Tournament last;
        private final int firstIndex, ordinal;
        private int lastIndex, milestones;
        private boolean latest;
        Reign(PartnerParticipant partner, Tournament first, int index, int ordinal) {
            this.partner=partner; this.first=first; this.last=first;
            this.firstIndex=index; this.lastIndex=index; this.ordinal=ordinal;
        }
        public String getTeamName() { return partner.getName(); }
        public String getPartnerId() { return partner.getId(); }
        public Tournament getFirst() { return first; }
        public Tournament getLast() { return last; }
        public int getFirstIndex() { return firstIndex; }
        public int getLastIndex() { return lastIndex; }
        public int getOrdinal() { return ordinal; }
        public int getMilestones() { return milestones; }
        public boolean isLatest() { return latest; }
    }
    public static final class Leader {
        private final PartnerParticipant partner;
        private final Tournament first;
        private Tournament last;
        private final int firstIndex;
        private int lastIndex, milestones, longestStreak;
        private boolean latest;
        private final List<Reign> reigns = new ArrayList<>();
        Leader(PartnerParticipant partner, Tournament tournament, int index) {
            this.partner=partner; first=tournament; last=tournament;
            firstIndex=index; lastIndex=index;
        }
        public String getTeamName() { return partner.getName(); }
        public String getPartnerId() { return partner.getId(); }
        public Tournament getFirst() { return first; }
        public Tournament getLast() { return last; }
        public int getFirstIndex() { return firstIndex; }
        public int getLastIndex() { return lastIndex; }
        public int getMilestones() { return milestones; }
        public int getLongestStreak() { return longestStreak; }
        public boolean isLatest() { return latest; }
        public List<Reign> getReigns() { return Collections.unmodifiableList(reigns); }
    }

    public static List<Leader> build(List<Tournament> tournaments, List<PartnerParticipant> partners,
            List<Map<String,Integer>> points, int window) {
        if (window < 1) throw new IllegalArgumentException("Window must be positive");
        Map<String,PartnerParticipant> ordered = new LinkedHashMap<>();
        if (partners != null) for (PartnerParticipant partner:partners) {
            if (partner.getName()!=null) ordered.put(partner.getName().trim().toLowerCase(),partner);
        }
        Map<String,Integer> totals = new HashMap<>();
        Map<String,Leader> leaders = new LinkedHashMap<>();
        String previous = null, latest = null;
        int streak = 0;
        int ordinal = 0;
        Reign activeReign = null;
        if (tournaments != null) for (int step=0;step<tournaments.size();step++) {
            String winner=null;
            int best=0;
            for (String key:ordered.keySet()) {
                int total=totals.getOrDefault(key,0)+point(points,step,key)-point(points,step-window,key);
                totals.put(key,total);
                // Strict > preserves partner order for ties, matching the canonical stable sort.
                if (total>best) { best=total; winner=key; }
            }
            Tournament tournament=tournaments.get(step);
            if (!"COMPLETED".equalsIgnoreCase(tournament.getStatus()) && !"FINISHED".equalsIgnoreCase(tournament.getStatus())) continue;
            latest=winner;
            if (winner==null) { previous=null; streak=0; activeReign=null; continue; }
            streak=winner.equals(previous)?streak+1:1;
            previous=winner;
            int index=tournament.getTournamentIndexInSeries()>0?tournament.getTournamentIndexInSeries():step+1;
            Leader leader=leaders.get(winner);
            if (leader==null) { leader=new Leader(ordered.get(winner),tournament,index); leaders.put(winner,leader); }
            if (streak==1) {
                activeReign=new Reign(ordered.get(winner),tournament,index,++ordinal);
                leader.reigns.add(activeReign);
            }
            activeReign.last=tournament;
            activeReign.lastIndex=index;
            activeReign.milestones++;
            leader.last=tournament;
            leader.lastIndex=index;
            leader.milestones++;
            leader.longestStreak=Math.max(leader.longestStreak,streak);
        }
        if (latest!=null) leaders.get(latest).latest=true;
        if (activeReign!=null) activeReign.latest=true;
        return new ArrayList<>(leaders.values());
    }

    private static int point(List<Map<String,Integer>> points,int index,String key) {
        if(points==null || index<0 || index>=points.size() || points.get(index)==null)return 0;
        return points.get(index).getOrDefault(key,0);
    }

    /** A change of leader is celebrated until the next completed tournament. */
    public static Reign announcement(List<Leader> history) {
        Reign latest=null, previous=null;
        if (history==null) return null;
        for (Leader leader:history) for (Reign reign:leader.reigns) {
            if (latest==null || reign.ordinal>latest.ordinal) { previous=latest; latest=reign; }
            else if (previous==null || reign.ordinal>previous.ordinal) previous=reign;
        }
        if (latest==null || previous==null || !latest.latest || latest.milestones!=1) return null;
        boolean same = latest.getPartnerId()!=null && previous.getPartnerId()!=null
                ? latest.getPartnerId().equalsIgnoreCase(previous.getPartnerId())
                : latest.getTeamName().trim().equalsIgnoreCase(previous.getTeamName().trim());
        return same?null:latest;
    }
}
