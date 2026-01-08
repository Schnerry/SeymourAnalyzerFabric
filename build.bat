@echo off
cd /d "%~dp0"
echo Building Seymour Analyzer...
call gradlew.bat build --console=plain
echo.
echo Build complete! Check build\libs\ for the JAR file.
pause

