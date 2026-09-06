package test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class TestHttpPostSync {
    public static void main(String[] args) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            String json = "[{\"name\":\"Francisco Sanchez Ruiz\",\"totalPts\":2570,\"activeTourneys\":4,\"rank\":1}," +
                          "{\"name\":\"Jayson Shaw\",\"totalPts\":2270,\"activeTourneys\":4,\"rank\":2}," +
                          "{\"name\":\"Albin Ouschan\",\"totalPts\":2120,\"activeTourneys\":5,\"rank\":3}," +
                          "{\"name\":\"Fedor Gorst\",\"totalPts\":2000,\"activeTourneys\":4,\"rank\":4}," +
                          "{\"name\":\"David Alcaide\",\"totalPts\":1670,\"activeTourneys\":5,\"rank\":5}," +
                          "{\"name\":\"Aloysius Yapp\",\"totalPts\":1610,\"activeTourneys\":5,\"rank\":6}]";

            String body = "action=syncClientStandings&seriesId=S_bc6c7365&standingsJson=" + URLEncoder.encode(json, StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/Tourma/rolling/standings"))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            System.out.println("HTTP Status: " + resp.statusCode());
            System.out.println("Response body: " + resp.body());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
