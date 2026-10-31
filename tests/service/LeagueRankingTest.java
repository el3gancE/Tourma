package service;

import java.util.*;

/** Run with javac/java; no database or tournament mutations. */
public class LeagueRankingTest {
    static class Row {
        String name; int points, difference, scored;
        Row(String n, int p, int d, int s) { name=n; points=p; difference=d; scored=s; }
    }
    static void check(String expected, Map<String,Map<String,Integer>> h2h, Row... input) {
        List<Row> rows=new ArrayList<>(Arrays.asList(input));
        LeagueRanking.sort(rows, r->r.name, r->r.name, r->r.points, r->r.difference, r->r.scored, h2h);
        String actual=String.join(",", rows.stream().map(r->r.name).toArray(String[]::new));
        if (!expected.equals(actual)) throw new AssertionError(expected+" != "+actual);
    }
    public static void main(String[] args) {
        Map<String,Map<String,Integer>> h=new HashMap<>();
        LeagueRanking.addHeadToHead(h,"Alpha","Beta",3);
        check("Alpha,Beta",h,new Row("Beta",6,2,20),new Row("Alpha",6,2,10));
        check("Beta,Alpha",h,new Row("Alpha",3,9,10),new Row("Beta",6,0,6));
        check("Beta,Alpha",h,new Row("Alpha",6,1,10),new Row("Beta",6,2,6));
        check("Beta,Alpha",Collections.emptyMap(),new Row("Alpha",6,2,10),new Row("Beta",6,2,20));
        check("Alpha,Beta",Collections.emptyMap(),new Row("Beta",6,2,10),new Row("Alpha",6,2,10));
        LeagueRanking.addHeadToHead(h,"Beta","Gamma",3);
        LeagueRanking.addHeadToHead(h,"Gamma","Alpha",3);
        check("Alpha,Beta,Gamma",h,new Row("Gamma",3,0,5),new Row("Beta",3,0,5),new Row("Alpha",3,0,5));
        LeagueRanking.addHeadToHead(h,"Alpha","Beta",2);
        if (h.get("Alpha").get("Beta")!=5) throw new AssertionError("Multiple legs must accumulate");
        System.out.println("LeagueRanking: 7 checks passed");
    }
}
