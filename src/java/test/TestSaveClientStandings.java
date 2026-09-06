package test;

import service.RollingWindowPointService;

public class TestSaveClientStandings {
    public static void main(String[] args) {
        String seriesId = "S_bc6c7365";
        String json = "[{\"name\":\"Francisco Sanchez Ruiz\",\"totalPts\":2570,\"activeTourneys\":4,\"rank\":1},{\"name\":\"Jayson Shaw\",\"totalPts\":2270,\"activeTourneys\":4,\"rank\":2},{\"name\":\"Albin Ouschan\",\"totalPts\":2120,\"activeTourneys\":5,\"rank\":3}]";
        boolean res = RollingWindowPointService.getInstance().saveClientStandings(seriesId, json);
        System.out.println("Result: " + res);
    }
}
