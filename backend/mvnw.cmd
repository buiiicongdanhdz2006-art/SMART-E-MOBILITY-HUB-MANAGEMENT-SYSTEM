@echo off
rem Maven wrapper (Windows): downloads Maven on first use, then runs it. Needs Java 17+.
rem   mvnw.cmd test
setlocal
set "BASEDIR=%~dp0"
for /f "usebackq tokens=1,* delims==" %%a in ("%BASEDIR%.mvn\wrapper\maven-wrapper.properties") do if "%%a"=="distributionUrl" set "URL=%%b"
for %%f in ("%URL%") do set "ZIPNAME=%%~nf"
set "NAME=%ZIPNAME:-bin=%"
if "%MAVEN_USER_HOME%"=="" set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "DIST=%MAVEN_USER_HOME%\wrapper\dists\%NAME%"
if not exist "%DIST%\%NAME%\bin\mvn.cmd" (
  echo Dang tai %NAME% ^(chi lan dau^)...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "New-Item -ItemType Directory -Force -Path '%DIST%' | Out-Null; Invoke-WebRequest -Uri '%URL%' -OutFile '%DIST%\download.zip'; Expand-Archive -Force -Path '%DIST%\download.zip' -DestinationPath '%DIST%'; Remove-Item '%DIST%\download.zip'"
  if errorlevel 1 exit /b 1
)
call "%DIST%\%NAME%\bin\mvn.cmd" -f "%BASEDIR%pom.xml" %*
