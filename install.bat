@echo off
cd /d "%~dp0"
if not exist adb.exe (
  echo adb.exe not found in this folder.
  pause
  exit /b 1
)
set APK=
for %%f in (*.apk) do set APK=%%f
if "%APK%"=="" (
  echo No .apk file found in this folder.
  pause
  exit /b 1
)
adb install -r "%APK%"
pause
