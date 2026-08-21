@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup script for Windows
@REM Generated for use with Maven 3.9.6
@REM ----------------------------------------------------------------------------

@echo off
setlocal

set MAVEN_WRAPPER_JAR=.mvn\wrapper\maven-wrapper.jar
set MAVEN_WRAPPER_PROPERTIES=.mvn\wrapper\maven-wrapper.properties

if defined JAVA_HOME (
    set JAVACMD=%JAVA_HOME%\bin\java.exe
) else (
    set JAVACMD=java.exe
)

if not exist "%MAVEN_WRAPPER_JAR%" (
    for /f "tokens=2 delims==" %%a in ('findstr /i "wrapperUrl" "%MAVEN_WRAPPER_PROPERTIES%"') do set WRAPPER_URL=%%a
    echo Downloading Maven Wrapper JAR from !WRAPPER_URL! ...
    powershell -Command "Invoke-WebRequest -Uri '!WRAPPER_URL!' -OutFile '%MAVEN_WRAPPER_JAR%'"
)

"%JAVACMD%" ^
    -classpath "%MAVEN_WRAPPER_JAR%" ^
    "-Dmaven.multiModuleProjectDirectory=%CD%" ^
    org.apache.maven.wrapper.MavenWrapperMain %*

endlocal
