<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%
    String configTournamentId = request.getParameter("id");
    if (configTournamentId == null) configTournamentId = request.getParameter("tournamentId");
    model.Tournament configTournament = null;
    if (configTournamentId != null && !configTournamentId.trim().isEmpty()) {
        configTournament = (model.Tournament) request.getAttribute("setupTournament");
        if (configTournament == null || !configTournamentId.trim().equals(configTournament.getId())) {
            configTournament = new dao.TournamentDAO().getTournamentById(configTournamentId.trim(), false);
        }
    }
    if (configTournament != null) {
        String configJson = configTournament.getMultiStageConfig();
        if (configJson == null || configJson.trim().isEmpty()) configJson = "null";
        String configType = configTournament.getTournamentType();
        String configFormat = configTournament.getFormat();
        String configStage2Format = configTournament.getStage2Format();
        int configAdvancingSeats = configTournament.getAdvancingSeatsCount();
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
    var stage2Format = decode('<%= java.util.Base64.getEncoder().encodeToString((configStage2Format == null ? "SINGLE_ELIMINATION" : configStage2Format).getBytes(java.nio.charset.StandardCharsets.UTF_8)) %>');
    var dbAdvancingSeats = <%= configAdvancingSeats %>;
    var config;
    try {
        config = JSON.parse(decode('<%= java.util.Base64.getEncoder().encodeToString(configJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)) %>'));
    } catch (e) { console.error('Cannot read saved tournament configuration', e); }
    if (!config && type === 'MULTI_STAGE' && dbAdvancingSeats > 0) {
        config = {
            stage1Format: format,
            stage2Format: stage2Format,
            stage1Config: format === 'GROUP_STAGE' ? { totalAdvanceCount: dbAdvancingSeats, advanceCount: dbAdvancingSeats } : { advanceCount: dbAdvancingSeats }
        };
    } else if (config && type === 'MULTI_STAGE') {
        if (!config.stage1Format) config.stage1Format = format;
        if (!config.stage2Format) config.stage2Format = stage2Format;
        config.stage1Config = config.stage1Config || {};
        if (dbAdvancingSeats > 0 && !config.stage1Config.advanceCount && !config.stage1Config.totalAdvanceCount) {
            config.stage1Config.advanceCount = dbAdvancingSeats;
            if (config.stage1Format === 'GROUP_STAGE') config.stage1Config.totalAdvanceCount = dbAdvancingSeats;
        }
    }
    window.TourmaTournamentType = type;
    window.TourmaTopSeedCount = <%= new dao.TournamentDAO().getTopSeedCount(configTournamentId.trim()) %>;
    if (config) window.TourmaMultiStageConfig = config;
    if (window.TourmaSeedDisplay && window.TourmaTopSeedCount !== null) {
        var seedSettings = window.TourmaSeedDisplay.read(id) || {};
        seedSettings.visibleCount = String(window.TourmaTopSeedCount);
        window.TourmaSeedDisplay.save(id, seedSettings);
    }
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
        var cut = type === 'MULTI_STAGE' ? (rules.totalAdvanceCount || rules.advanceCount || dbAdvancingSeats || 0) : 0;
        localStorage.setItem('tourma_advance_count_' + id, String(cut));
        localStorage.setItem('tourma_cut_target_' + id, String(cut));
        if (config.stage1Format === 'GSL' && cut > 0) {
            localStorage.setItem('tourma_gsl_advance_count_' + id, String(cut));
        }
    } catch (e) { console.warn('Cannot cache tournament configuration', e); }
})();
</script>
<% } %>
