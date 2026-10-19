<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%
    String configTournamentId = request.getParameter("id");
    if (configTournamentId == null) configTournamentId = request.getParameter("tournamentId");
    model.Tournament configTournament = null;
    if (configTournamentId != null && !configTournamentId.trim().isEmpty()) {
        configTournament = new dao.TournamentDAO().getTournamentById(configTournamentId.trim());
    }
    if (configTournament != null) {
        String configJson = configTournament.getMultiStageConfig();
        if (configJson == null || configJson.trim().isEmpty()) configJson = "null";
        String configType = configTournament.getTournamentType();
        String configFormat = configTournament.getFormat();
%>
<script>
(function () {
    // Encode server values so JSON and team names cannot terminate this script.
    function decode(value) {
        return new TextDecoder().decode(Uint8Array.from(atob(value), function (c) { return c.charCodeAt(0); }));
    }
    var id = decode('<%= java.util.Base64.getEncoder().encodeToString(configTournamentId.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8)) %>');
    var type = decode('<%= java.util.Base64.getEncoder().encodeToString((configType == null ? "SINGLE_STAGE" : configType).getBytes(java.nio.charset.StandardCharsets.UTF_8)) %>');
    var format = decode('<%= java.util.Base64.getEncoder().encodeToString((configFormat == null ? "SINGLE_ELIMINATION" : configFormat).getBytes(java.nio.charset.StandardCharsets.UTF_8)) %>');
    var config;
    try {
        config = JSON.parse(decode('<%= java.util.Base64.getEncoder().encodeToString(configJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)) %>'));
    } catch (e) { console.error('Cannot read saved tournament configuration', e); }
    window.TourmaTournamentType = type;
    if (config) window.TourmaMultiStageConfig = config;
    try {
        localStorage.setItem('tourma_type_' + id, type);
        localStorage.setItem('tourma_format_' + id, format);
        if (!config) return; // Legacy local-only settings can still be saved from the configuration page.
        localStorage.setItem('tourma_multi_config_' + id, JSON.stringify(config));
        var rules = config.stage1Config || {};
        if (config.stage1Format === 'ROUND_ROBIN') {
            localStorage.setItem('tourma_rr_config_' + id, JSON.stringify(rules));
        }
        if (config.stage1Format === 'GROUP_STAGE') {
            localStorage.setItem('tourma_group_config_' + id, JSON.stringify(rules));
        }
        var cut = type === 'MULTI_STAGE' ? (rules.totalAdvanceCount || rules.advanceCount || 0) : 0;
        localStorage.setItem('tourma_advance_count_' + id, String(cut));
        localStorage.setItem('tourma_cut_target_' + id, String(cut));
    } catch (e) { console.warn('Cannot cache tournament configuration', e); }
})();
</script>
<% } %>
