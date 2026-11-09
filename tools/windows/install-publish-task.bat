@echo off
rem One-time setup: daily 10:07 task that publishes the monitoring results (publish-hidden.vbs -> publish.bat).
rem Remove: schtasks /Delete /TN "Barajyq\Publish feed" /F
setlocal
set "LOG=%~dp0..\..\_claude\monitoring\install-task.log"
if not exist "%~dp0..\..\_claude\monitoring" mkdir "%~dp0..\..\_claude\monitoring"
schtasks /Create /F /SC DAILY /ST 10:07 /TN "Barajyq\Publish feed" /TR "wscript.exe \"%~dp0publish-hidden.vbs\"" > "%LOG%" 2>&1
echo CREATE_EXIT=%ERRORLEVEL% >> "%LOG%"
schtasks /Query /TN "Barajyq\Publish feed" /FO LIST >> "%LOG%" 2>&1
endlocal
