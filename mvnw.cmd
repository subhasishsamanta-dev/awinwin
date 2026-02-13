@echo off
setlocal
:: Simple Maven wrapper for Windows: downloads Maven locally if not installed and runs it
where mvn >nul 2>&1
if %ERRORLEVEL%==0 (
  mvn %*
  exit /b %ERRORLEVEL%
)
set MAVEN_VERSION=3.9.6
set WRAPPER_DIR=%~dp0.mvn\wrapper
set MAVEN_ZIP=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%-bin.zip
set MAVEN_DIR=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%
if not exist "%MAVEN_DIR%\bin\mvn.cmd" (
  if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
  if not exist "%MAVEN_ZIP%" (
    powershell -Command "Write-Host 'Downloading Maven %MAVEN_VERSION%...'; Invoke-WebRequest -Uri 'https://dlcdn.apache.org/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile '%MAVEN_ZIP%'"
  )
  powershell -Command "Write-Host 'Extracting Maven...'; Expand-Archive -Force -LiteralPath '%MAVEN_ZIP%' -DestinationPath '%WRAPPER_DIR%'"
)
"%MAVEN_DIR%\bin\mvn.cmd" %*
endlocal
exit /b %ERRORLEVEL%
