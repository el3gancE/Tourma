@echo off
setlocal enabledelayedexpansion

set "CP=web\WEB-INF\lib\*;C:\Users\admin\.m2\repository\jakarta\servlet\jakarta.servlet-api\6.0.0\jakarta.servlet-api-6.0.0.jar;C:\Users\admin\.m2\repository\jakarta\servlet\jsp\jakarta.servlet.jsp-api\3.1.0\jakarta.servlet.jsp-api-3.1.0.jar"

if not exist build\web\WEB-INF\classes mkdir build\web\WEB-INF\classes
if not exist build\classes mkdir build\classes

dir /s /b src\java\*.java > sources.txt
javac -encoding UTF-8 -cp "%CP%" -d build\web\WEB-INF\classes @sources.txt
set ERR=%ERRORLEVEL%
if %ERR% EQU 0 (
    javac -encoding UTF-8 -cp "%CP%" -d build\classes @sources.txt
    xcopy /E /I /Y /Q web build\web >nul 2>&1
)
del sources.txt

if %ERR% EQU 0 (
    echo [BUILD SUCCESS] All Java files compiled and web assets synced cleanly!
) else (
    echo [BUILD ERROR] Compilation failed with error code %ERR%
)

exit /b %ERR%
