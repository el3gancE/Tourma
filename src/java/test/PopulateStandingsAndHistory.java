package test;

import dao.DBContext;
import dao.ParticipantDAO;
import dao.SeriesDAO;
import dao.TournamentDAO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import model.PartnerParticipant;
import model.Series;
import model.Team;
import model.Tournament;
import service.RollingWindowPointService;

public class PopulateStandingsAndHistory {
    public static void main(String[] args) {
        SeriesDAO seriesDAO = new SeriesDAO();
        List<Series> allSeries = seriesDAO.getAllSeries();
        System.out.println("Found " + allSeries.size() + " series in DB.");

        RollingWindowPointService service = RollingWindowPointService.getInstance();
        ParticipantDAO pDao = new ParticipantDAO();
        DBContext db = new DBContext();

        for (Series s : allSeries) {
            String seriesId = s.getId();
            System.out.println("\nProcessing Series: " + s.getName() + " (" + seriesId + ")");

            List<RollingWindowPointService.RollingStandingDTO> standings = service.calculateSeriesStandingsWithExpiry(seriesId);
            System.out.println("Calculated " + standings.size() + " standings for series " + seriesId);

            List<Tournament> allTourneys = seriesDAO.getTournamentsBySeriesId(seriesId);
            System.out.println("Found " + allTourneys.size() + " tournaments in series.");

            try (Connection conn = db.getConnection()) {
                conn.setAutoCommit(false);

                // 1. Delete old standings & history for this series
                try (PreparedStatement psDelS = conn.prepareStatement("DELETE FROM series_standings WHERE series_id = ?");
                     PreparedStatement psDelH = conn.prepareStatement("DELETE FROM series_tournament_history WHERE series_id = ?")) {
                    psDelS.setString(1, seriesId);
                    psDelS.executeUpdate();
                    psDelH.setString(1, seriesId);
                    psDelH.executeUpdate();
                }

                // 2. Insert into series_standings
                String sqlInsertStanding = "INSERT INTO series_standings (id, series_id, phase_number, normalized_team_name, " +
                        "partner_participant_id, group_name, total_rolling_points, current_elo, matches_played, rank_overall, updated_at) " +
                        "VALUES (?, ?, 1, ?, ?, 'General', ?, 1000.0, ?, ?, CURRENT_TIMESTAMP)";

                try (PreparedStatement psS = conn.prepareStatement(sqlInsertStanding)) {
                    int r = 1;
                    for (RollingWindowPointService.RollingStandingDTO dto : standings) {
                        String standingId = "ST_" + seriesId + "_" + (dto.getPartnerParticipantId() != null ? dto.getPartnerParticipantId() : ("T_" + r));
                        psS.setString(1, standingId);
                        psS.setString(2, seriesId);
                        psS.setString(3, dto.getTeamName());
                        psS.setString(4, dto.getPartnerParticipantId());
                        psS.setInt(5, dto.getTotalActivePoints());
                        psS.setInt(6, dto.getActiveTourneysCount());
                        psS.setInt(7, dto.getRank() > 0 ? dto.getRank() : r);
                        psS.addBatch();
                        r++;
                    }
                    int[] standingResults = psS.executeBatch();
                    System.out.println("Inserted " + standingResults.length + " rows into series_standings.");
                }

                // 3. Insert into series_tournament_history
                String sqlInsertHistory = "INSERT INTO series_tournament_history (id, series_id, tournament_id, phase_number, " +
                        "normalized_team_name, tournament_rank, points_earned, points_deducted, elo_change, completed_at) " +
                        "VALUES (?, ?, ?, 1, ?, ?, ?, 0, 0.0, ?)";

                try (PreparedStatement psH = conn.prepareStatement(sqlInsertHistory)) {
                    int histCount = 0;
                    for (Tournament t : allTourneys) {
                        String tCfgRaw = t.getSeriesPointsConfig();
                        if (tCfgRaw == null || tCfgRaw.trim().isEmpty() || !tCfgRaw.trim().startsWith("{")) {
                            tCfgRaw = "{\"1\":500,\"2\":200,\"3-4\":100,\"5-8\":0}";
                        }
                        Map<String, Integer> posPtsMap = service.parsePointsConfigJson(tCfgRaw);
                        Map<String, Integer> placements = pDao.getTournamentPlacements(t.getId());
                        List<Team> tourneyTeams = pDao.getTeamsByTournamentId(t.getId());

                        if (tourneyTeams != null) {
                            for (Team tm : tourneyTeams) {
                                if (tm.getRawName() == null) continue;
                                Integer matchPos = placements.get(tm.getId());
                                if (matchPos == null) {
                                    matchPos = placements.get(tm.getRawName().trim().toLowerCase());
                                }
                                int pos = (matchPos != null && matchPos > 0) ? matchPos : tm.getOriginalSeed();
                                int pts = service.resolvePointsForPosition(pos, posPtsMap);

                                String histId = "H_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
                                psH.setString(1, histId);
                                psH.setString(2, seriesId);
                                psH.setString(3, t.getId());
                                psH.setString(4, tm.getNormalizedName() != null ? tm.getNormalizedName() : tm.getRawName());
                                psH.setInt(5, pos);
                                psH.setInt(6, pts);
                                psH.setTimestamp(7, t.getCreatedAt() != null ? t.getCreatedAt() : new java.sql.Timestamp(System.currentTimeMillis()));
                                psH.addBatch();
                                histCount++;
                            }
                        }
                    }
                    int[] histResults = psH.executeBatch();
                    System.out.println("Inserted " + histResults.length + " rows into series_tournament_history.");
                }

                conn.commit();
                System.out.println("Committed successfully for Series " + s.getName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
