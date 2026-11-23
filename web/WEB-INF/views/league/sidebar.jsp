<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<c:url var="leagueDashboardUrl" value="/league/dashboard">
    <c:param name="id" value="${leagueSeries.id}" />
</c:url>
<c:url var="leagueTeamUrl" value="/league/season/teams"><c:param name="id" value="${leagueSeries.id}" /></c:url>
<details class="tournament-navigation" open>
    <summary>Điều hướng League</summary>
    <aside class="app-left-sidebar tournament-sidebar" aria-label="Điều hướng League">
        <div class="sidebar-workspace-heading">
            <span class="sidebar-workspace-mark" aria-hidden="true"><i class="fa-solid fa-layer-group"></i></span>
            <span>League System</span>
        </div>
        <nav class="sidebar-section" aria-label="Chuỗi giải League">
            <ul class="sidebar-menu-list">
                <li class="sidebar-menu-item">
                    <a class="sidebar-menu-link ${empty leagueSection ? 'active' : ''}" aria-current="${empty leagueSection ? 'page' : 'false'}" href="<c:out value='${leagueDashboardUrl}' />">
                        <i class="fa-solid fa-table-columns" aria-hidden="true"></i> Tổng quan
                    </a>
                </li>
                <li class="sidebar-menu-item">
                    <a class="sidebar-menu-link" href="<c:out value='${leagueDashboardUrl}' />#league-seasons">
                        <i class="fa-regular fa-calendar" aria-hidden="true"></i> Mùa giải
                    </a>
                </li>
                <li class="sidebar-menu-item">
                    <a class="sidebar-menu-link ${leagueSection == 'teams' ? 'active' : ''}" aria-current="${leagueSection == 'teams' ? 'page' : 'false'}" href="<c:out value='${leagueTeamUrl}' />">
                        <i class="fa-solid fa-users" aria-hidden="true"></i> Thêm đội
                    </a>
                </li>
            </ul>
        </nav>
    </aside>
</details>
