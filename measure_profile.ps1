$content = (Invoke-WebRequest -Uri 'http://localhost:8080/Tourma/team-profile?seriesId=S_5d2002f6&teamName=Shane+Van+Boening' -UseBasicParsing).Content
Write-Host "Total length: $($content.Length)"
$pAch = $content.IndexOf("window.serverTourneyAchievements")
$pPhase = $content.IndexOf("window.seriesPhaseSize")
$pPts = $content.IndexOf("window.serverTourneyPoints")
$pPart = $content.IndexOf("window.serverTourneyParticipation")
$pProg = $content.IndexOf("window.teamRankProgression")

Write-Host "window.seriesSubTournaments: $($content.IndexOf('window.seriesSubTournaments'))"
Write-Host "window.serverTourneyPoints: $pPts"
Write-Host "window.serverTourneyParticipation: $pPart"
Write-Host "window.teamRankProgression: $pProg"
Write-Host "window.serverTourneyAchievements: $pAch"
Write-Host "window.seriesPhaseSize: $pPhase"
Write-Host "serverTourneyAchievements size: $($content.IndexOf('</script>', $pAch) - $pAch)"

