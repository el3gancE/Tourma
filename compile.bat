@echo off
setlocal enabledelayedexpansion

set "CP=web\WEB-INF\lib\*;C:\Users\admin\.m2\repository\jakarta\servlet\jakarta.servlet-api\6.0.0\jakarta.servlet-api-6.0.0.jar;C:\Users\admin\.m2\repository\jakarta\servlet\jsp\jakarta.servlet.jsp-api\3.1.0\jakarta.servlet.jsp-api-3.1.0.jar"

if not exist build\web\WEB-INF\classes mkdir build\web\WEB-INF\classes

dir /s /b src\java\*.java > sources.txt
javac -encoding UTF-8 -cp "%CP%" -d build\web\WEB-INF\classes @sources.txt
set ERR=%ERRORLEVEL%
del sources.txt

if %ERR% EQU 0 (
    echo [BUILD SUCCESS] All Java files compiled cleanly!
) else (
    echo [BUILD ERROR] Compilation failed with error code %ERR%
)

exit /b %ERR%
