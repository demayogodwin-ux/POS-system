@echo off
rem Builds target\installer\MyStorePOS-1.0.0.exe
rem Needs: JDK 17+ with jpackage (this PC has JDK 25) and WiX Toolset 3.14 on PATH.
rem Maven is fetched by mvnw.cmd on first run.
setlocal

cd /d "%~dp0"

echo === 1/2  Tests and fat jar (target\dist) ===
call "%~dp0mvnw.cmd" -B clean package || goto :error

echo === 2/2  Windows installer (target\installer) ===
jpackage ^
  --type exe ^
  --name MyStorePOS ^
  --app-version 1.0.0 ^
  --vendor "My Store" ^
  --description "My Store point of sale" ^
  --input target\dist ^
  --main-jar pos-1.0.0.jar ^
  --main-class com.pos.app.Launcher ^
  --add-modules java.base,java.desktop,jdk.httpserver,java.management,java.naming,java.scripting,java.security.sasl,java.sql,jdk.jfr,jdk.unsupported,jdk.localedata ^
  --java-options "--enable-native-access=ALL-UNNAMED" ^
  --win-per-user-install ^
  --win-dir-chooser ^
  --win-menu ^
  --win-menu-group "My Store POS" ^
  --win-shortcut ^
  --win-upgrade-uuid 0732adb2-7f31-4b4c-8a53-ee444f894d94 ^
  --dest target\installer || goto :error

echo.
echo Done: target\installer\MyStorePOS-1.0.0.exe
echo After installing, edit %%LOCALAPPDATA%%\MyStorePOS\app\.env to change the database server.
exit /b 0

:error
echo.
echo BUILD FAILED - see the messages above.
exit /b 1
