@echo off
setlocal

set "APP_HOME=%~dp0"
set "WRAPPER_DIR=%APP_HOME%gradle\wrapper"
set "WRAPPER_JAR=%WRAPPER_DIR%\gradle-wrapper.jar"
set "WRAPPER_BASE64=%WRAPPER_JAR%.base64"
set "WRAPPER_SHA=%WRAPPER_JAR%.sha256"

powershell -NoProfile -ExecutionPolicy Bypass -Command "$jar='%WRAPPER_JAR%'; $encodedFile='%WRAPPER_BASE64%'; $expected=(Get-Content -Raw '%WRAPPER_SHA%').Trim().ToLowerInvariant(); if (!(Test-Path -LiteralPath $jar)) { $encoded=([IO.File]::ReadAllText($encodedFile)); [IO.File]::WriteAllBytes($jar,[Convert]::FromBase64String($encoded)) }; $actual=(Get-FileHash -Algorithm SHA256 -LiteralPath $jar).Hash.ToLowerInvariant(); if ($actual -ne $expected) { Remove-Item -LiteralPath $jar -Force -ErrorAction SilentlyContinue; exit 2 }"
if errorlevel 1 (
    echo Failed to restore or verify the Gradle wrapper JAR.
    exit /b 1
)

if defined JAVA_HOME (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_EXE=java.exe"
)

"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
set "EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %EXIT_CODE%
