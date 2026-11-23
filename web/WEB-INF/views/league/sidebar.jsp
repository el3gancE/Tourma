<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<c:url var="leagueDashboardUrl" value="/league/dashboard">
    <c:param name="id" value="${leagueSeries.id}" />
</c:url>
<c:url var="leagueTeamUrl" value="/league/season/teams"><c:param name="id" value="${leagueSeries.id}" /></c:url>
<c:url var="leagueSeasonUrl" value="/league/season/create"><c:param name="id" value="${leagueSeries.id}" /></c:url>
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
                    <a class="sidebar-menu-link ${empty leagueSection and empty leagueCurrentSeasonId ? 'active' : ''}" aria-current="${empty leagueSection and empty leagueCurrentSeasonId ? 'page' : 'false'}" href="<c:out value='${leagueDashboardUrl}' />">
                        <i class="fa-solid fa-table-columns" aria-hidden="true"></i> Tổng quan
                    </a>
                </li>
                <li class="sidebar-menu-item">
                    <a class="sidebar-menu-link ${leagueSection == 'teams' ? 'active' : ''}" aria-current="${leagueSection == 'teams' ? 'page' : 'false'}" href="<c:out value='${leagueTeamUrl}' />">
                        <i class="fa-solid fa-users" aria-hidden="true"></i> Đội tham gia
                    </a>
                </li>
                <c:if test="${empty leagueLatestSeason or leagueLatestSeason.status != 'ACTIVE'}">
                    <li class="sidebar-menu-item">
                        <a class="sidebar-menu-link ${leagueSection == 'seasons' ? 'active' : ''}" aria-current="${leagueSection == 'seasons' ? 'page' : 'false'}" href="<c:out value='${leagueSeasonUrl}' />">
                            <i class="fa-solid fa-sliders" aria-hidden="true"></i>
                            ${empty leagueLatestSeason ? 'Tạo mùa đầu tiên' : (leagueLatestSeason.status == 'DRAFT' ? 'Thiết lập mùa' : 'Tạo mùa kế tiếp')}
                        </a>
                    </li>
                </c:if>
            </ul>
        </nav>
        <c:if test="${not empty leagueSeasons}">
            <nav class="sidebar-section" aria-label="Mùa giải">
                <div class="sidebar-section-header">Mùa giải</div>
                <ul class="sidebar-menu-list">
                    <c:forEach var="item" items="${leagueSeasons}">
                        <c:url var="itemUrl" value="/league/season/dashboard">
                            <c:param name="id" value="${leagueSeries.id}" /><c:param name="seasonId" value="${item.id}" />
                        </c:url>
                        <li class="sidebar-menu-item">
                            <a class="sidebar-menu-link ${leagueCurrentSeasonId == item.id ? 'active' : ''}"
                               aria-current="${leagueCurrentSeasonId == item.id ? 'true' : 'false'}"
                               href="<c:out value='${itemUrl}' />">
                                <i class="fa-regular fa-calendar" aria-hidden="true"></i>
                                <span><c:out value="${item.name}" /></span>
                            </a>
                        </li>
                    </c:forEach>
                </ul>
            </nav>
        </c:if>
    </aside>
</details>
