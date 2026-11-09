@echo off
rem Barajyq: commit + push the feed files updated by the morning monitoring. Run by Task Scheduler
rem ("Barajyq\Publish feed", see install-publish-task.bat). Only data files are staged, never code.
setlocal EnableDelayedExpansion
set "ROOT=%~dp0..\.."
cd /d "%ROOT%"
if not exist "_claude\monitoring" mkdir "_claude\monitoring"
set "LOG=%CD%\_claude\monitoring\publish.log"
echo === %date% %time% >> "%LOG%"
git add -- feed/events.json android/app/src/main/assets/events.json tools/feed_data.json tools/tg_cursor.json tools/ig_seen.json >> "%LOG%" 2>&1
git diff --cached --quiet
if "%ERRORLEVEL%"=="0" (
  echo nothing new to commit >> "%LOG%"
) else (
  git -c user.name=hexusArgon -c user.email=hexusArgon@users.noreply.github.com commit -q -m "feed: morning monitoring %date%" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>" >> "%LOG%" 2>&1
  echo COMMIT_EXIT=!ERRORLEVEL! >> "%LOG%"
)
set "GIT_TERMINAL_PROMPT=0"
set "GCM_INTERACTIVE=never"
git push origin main >> "%LOG%" 2>&1
echo PUSH_EXIT=%ERRORLEVEL% >> "%LOG%"
endlocal
