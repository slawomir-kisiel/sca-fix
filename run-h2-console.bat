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

echo [sca-fix-tool] Starting application with H2 Console on http://localhost:8080/h2-console ...
start "SCA-fix-tool" cmd /c "java -jar backend\target\sca-fix-backend-0.1.0-SNAPSHOT.jar"

echo [sca-fix-tool] Waiting for the application...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$deadline=(Get-Date).AddSeconds(60); while((Get-Date) -lt $deadline) { try { $connection=Test-NetConnection -ComputerName localhost -Port 8080 -WarningAction SilentlyContinue; if($connection.TcpTestSucceeded) { Start-Process 'http://localhost:8080/h2-console'; exit 0 } } catch {} Start-Sleep -Seconds 1 }; Write-Error 'Application did not become available on port 8080'; exit 1"
if errorlevel 1 (
    echo.
    echo [sca-fix-tool] Application did not become available. Check the SCA-fix-tool window for details.
    exit /b 1
)

echo [sca-fix-tool] H2 Console opened in the browser.
echo [sca-fix-tool] JDBC URL: jdbc:h2:file:./data/sca-fix;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
echo [sca-fix-tool] User: sa
echo [sca-fix-tool] Password: empty
exit /b 0
