@echo off
setlocal

cd /d "%~dp0"

if not exist "logs" mkdir "logs"

echo [sca-fix-tool] Building backend and frontend...
call mvn -pl backend -am package -DskipTests
if errorlevel 1 (
    echo.
    echo [sca-fix-tool] Build failed. Application was not started.
    exit /b 1
)

echo [sca-fix-tool] Starting application on http://localhost:8080 ...
start "SCA-fix-tool" cmd /c "java -jar backend\target\sca-fix-backend-0.1.0-SNAPSHOT.jar"

echo [sca-fix-tool] Waiting for the application...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$deadline=(Get-Date).AddSeconds(60); while((Get-Date) -lt $deadline) { try { $connection=Test-NetConnection -ComputerName localhost -Port 8080 -WarningAction SilentlyContinue; if($connection.TcpTestSucceeded) { Start-Process 'http://localhost:8080'; exit 0 } } catch {} Start-Sleep -Seconds 1 }; Write-Error 'Application did not become available on port 8080'; exit 1"
if errorlevel 1 (
    echo.
    echo [sca-fix-tool] Application did not become available. Check the SCA-fix-tool window for details.
    exit /b 1
)

echo [sca-fix-tool] Browser opened. Close the SCA-fix-tool window to stop the application.
exit /b 0
