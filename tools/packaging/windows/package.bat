@echo off
REM Materia Tools - Windows Desktop Packaging Script
REM Creates Windows installer and portable packages

setlocal enabledelayedexpansion

echo ========================================
echo Materia Tools - Windows Packaging
echo ========================================

REM Configuration
set APP_NAME=Materia-Tools
set APP_VERSION=1.0.0
set JAVA_VERSION=17
set BUILD_DIR=%cd%\build
set DIST_DIR=%cd%\dist
set RESOURCES_DIR=%cd%\resources

REM Clean previous builds
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
mkdir "%BUILD_DIR%"
mkdir "%DIST_DIR%"

echo Building Kotlin application...

REM Build the Kotlin application
cd ..\..\..\
call gradlew.bat :tools:editor:desktopJar
call gradlew.bat :tools:profiler:desktopJar
call gradlew.bat :tools:api-server:shadowJar

if %ERRORLEVEL% neq 0 (
    echo Build failed!
    exit /b 1
)

echo Build completed successfully.

REM Copy application JARs
copy "tools\editor\desktop\build\libs\*.jar" "%BUILD_DIR%\"
copy "tools\profiler\desktop\build\libs\*.jar" "%BUILD_DIR%\"
copy "tools\api-server\build\libs\*-all.jar" "%BUILD_DIR%\materia-api-server.jar"

echo Creating JRE bundle with jlink...

REM Create custom JRE
jlink --module-path "%JAVA_HOME%\jmods" ^
      --add-modules java.base,java.desktop,java.logging,java.management,java.naming,java.security.jgss,java.security.sasl,java.sql,jdk.crypto.ec,jdk.unsupported ^
      --output "%BUILD_DIR%\jre" ^
      --compress=2 ^
      --no-header-files ^
      --no-man-pages

if %ERRORLEVEL% neq 0 (
    echo JRE creation failed!
    exit /b 1
)

echo Creating application structure...

REM Create application structure
mkdir "%BUILD_DIR%\app"
mkdir "%BUILD_DIR%\app\bin"
mkdir "%BUILD_DIR%\app\lib"
mkdir "%BUILD_DIR%\app\resources"

REM Copy JARs to lib directory
copy "%BUILD_DIR%\*.jar" "%BUILD_DIR%\app\lib\"

REM Create launcher scripts
echo @echo off > "%BUILD_DIR%\app\bin\materia-tools.bat"
echo cd /d "%%~dp0\.." >> "%BUILD_DIR%\app\bin\materia-tools.bat"
echo jre\bin\java -jar lib\materia-api-server.jar %%* >> "%BUILD_DIR%\app\bin\materia-tools.bat"

echo @echo off > "%BUILD_DIR%\app\bin\scene-editor.bat"
echo cd /d "%%~dp0\.." >> "%BUILD_DIR%\app\bin\scene-editor.bat"
echo jre\bin\java -jar lib\editor-desktop.jar %%* >> "%BUILD_DIR%\app\bin\scene-editor.bat"

echo @echo off > "%BUILD_DIR%\app\bin\profiler.bat"
echo cd /d "%%~dp0\.." >> "%BUILD_DIR%\app\bin\profiler.bat"
echo jre\bin\java -jar lib\profiler-desktop.jar %%* >> "%BUILD_DIR%\app\bin\profiler.bat"

REM Copy resources
copy "%RESOURCES_DIR%\*" "%BUILD_DIR%\app\resources\" 2>nul

REM Copy JRE
xcopy /e /i "%BUILD_DIR%\jre" "%BUILD_DIR%\app\jre"

echo Creating portable ZIP package...

REM Create ZIP package
powershell -Command "& {Compress-Archive -Path '%BUILD_DIR%\app\*' -DestinationPath '%DIST_DIR%\%APP_NAME%-Windows-Portable.zip' -CompressionLevel Optimal}"

echo Creating MSI installer...

REM Create WiX installer (requires WiX Toolset)
if exist "%WIX%\bin\candle.exe" (
    echo Building MSI installer with WiX...

    REM Generate WiX source
    call :GenerateWixSource

    REM Compile WiX
    "%WIX%\bin\candle.exe" "%BUILD_DIR%\installer.wxs" -out "%BUILD_DIR%\installer.wixobj"
    "%WIX%\bin\light.exe" "%BUILD_DIR%\installer.wixobj" -out "%DIST_DIR%\%APP_NAME%-Setup.msi" -ext WixUIExtension

    if %ERRORLEVEL% equ 0 (
        echo MSI installer created successfully.
    ) else (
        echo MSI installer creation failed.
    )
) else (
    echo WiX Toolset not found. MSI installer creation skipped.
    echo Install WiX Toolset from https://wixtoolset.org/
)

echo Creating NSIS installer...

REM Create NSIS installer (requires NSIS)
if exist "%PROGRAMFILES%\NSIS\makensis.exe" (
    echo Building NSIS installer...
    call :GenerateNSISScript
    "%PROGRAMFILES%\NSIS\makensis.exe" "%BUILD_DIR%\installer.nsi"

    if %ERRORLEVEL% equ 0 (
        echo NSIS installer created successfully.
    ) else (
        echo NSIS installer creation failed.
    )
) else (
    echo NSIS not found. NSIS installer creation skipped.
    echo Install NSIS from https://nsis.sourceforge.io/
)

echo.
echo ========================================
echo Packaging completed!
echo ========================================
echo Portable ZIP: %DIST_DIR%\%APP_NAME%-Windows-Portable.zip
echo MSI Installer: %DIST_DIR%\%APP_NAME%-Setup.msi
echo NSIS Installer: %DIST_DIR%\%APP_NAME%-Setup.exe
echo ========================================

goto :eof

:GenerateWixSource
echo ^<?xml version="1.0" encoding="UTF-8"?^> > "%BUILD_DIR%\installer.wxs"
echo ^<Wix xmlns="http://schemas.microsoft.com/wix/2006/wi"^> >> "%BUILD_DIR%\installer.wxs"
echo   ^<Product Id="*" Name="%APP_NAME%" Language="1033" Version="%APP_VERSION%" Manufacturer="Materia Project" UpgradeCode="{12345678-1234-1234-1234-123456789012}"^> >> "%BUILD_DIR%\installer.wxs"
echo     ^<Package InstallerVersion="200" Compressed="yes" InstallScope="perMachine" /^> >> "%BUILD_DIR%\installer.wxs"
echo     ^<Media Id="1" Cabinet="app.cab" EmbedCab="yes" /^> >> "%BUILD_DIR%\installer.wxs"
echo     ^<Directory Id="TARGETDIR" Name="SourceDir"^> >> "%BUILD_DIR%\installer.wxs"
echo       ^<Directory Id="ProgramFilesFolder"^> >> "%BUILD_DIR%\installer.wxs"
echo         ^<Directory Id="INSTALLFOLDER" Name="%APP_NAME%" /^> >> "%BUILD_DIR%\installer.wxs"
echo       ^</Directory^> >> "%BUILD_DIR%\installer.wxs"
echo     ^</Directory^> >> "%BUILD_DIR%\installer.wxs"
echo     ^<Feature Id="ProductFeature" Title="%APP_NAME%" Level="1"^> >> "%BUILD_DIR%\installer.wxs"
echo       ^<ComponentGroupRef Id="ProductComponents" /^> >> "%BUILD_DIR%\installer.wxs"
echo     ^</Feature^> >> "%BUILD_DIR%\installer.wxs"
echo   ^</Product^> >> "%BUILD_DIR%\installer.wxs"
echo ^</Wix^> >> "%BUILD_DIR%\installer.wxs"
goto :eof

:GenerateNSISScript
echo !define APP_NAME "%APP_NAME%" > "%BUILD_DIR%\installer.nsi"
echo !define APP_VERSION "%APP_VERSION%" >> "%BUILD_DIR%\installer.nsi"
echo Name "${APP_NAME}" >> "%BUILD_DIR%\installer.nsi"
echo OutFile "%DIST_DIR%\${APP_NAME}-Setup.exe" >> "%BUILD_DIR%\installer.nsi"
echo InstallDir "$PROGRAMFILES\${APP_NAME}" >> "%BUILD_DIR%\installer.nsi"
echo Section "Install" >> "%BUILD_DIR%\installer.nsi"
echo   SetOutPath "$INSTDIR" >> "%BUILD_DIR%\installer.nsi"
echo   File /r "%BUILD_DIR%\app\*" >> "%BUILD_DIR%\installer.nsi"
echo   CreateShortCut "$DESKTOP\${APP_NAME}.lnk" "$INSTDIR\bin\materia-tools.bat" >> "%BUILD_DIR%\installer.nsi"
echo SectionEnd >> "%BUILD_DIR%\installer.nsi"
goto :eof