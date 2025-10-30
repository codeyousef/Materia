#!/bin/bash
# Materia Tools - macOS Desktop Packaging Script
# Creates macOS app bundle and DMG installer

set -e

echo "========================================"
echo "Materia Tools - macOS Packaging"
echo "========================================"

# Configuration
APP_NAME="Materia Tools"
APP_BUNDLE_NAME="Materia-Tools.app"
APP_VERSION="1.0.0"
BUNDLE_ID="dev.materia.tools"
JAVA_VERSION="17"
BUILD_DIR="$(pwd)/build"
DIST_DIR="$(pwd)/dist"
RESOURCES_DIR="$(pwd)/resources"

# Clean previous builds
rm -rf "$BUILD_DIR"
rm -rf "$DIST_DIR"
mkdir -p "$BUILD_DIR"
mkdir -p "$DIST_DIR"

echo "Building Kotlin application..."

# Build the Kotlin application
cd ../../../
./gradlew :tools:editor:desktopJar
./gradlew :tools:profiler:desktopJar
./gradlew :tools:api-server:shadowJar

echo "Build completed successfully."

# Copy application JARs
cp tools/editor/desktop/build/libs/*.jar "$BUILD_DIR/"
cp tools/profiler/desktop/build/libs/*.jar "$BUILD_DIR/"
cp tools/api-server/build/libs/*-all.jar "$BUILD_DIR/materia-api-server.jar"

echo "Creating custom JRE with jlink..."

# Create custom JRE
$JAVA_HOME/bin/jlink \
    --module-path $JAVA_HOME/jmods \
    --add-modules java.base,java.desktop,java.logging,java.management,java.naming,java.security.jgss,java.security.sasl,java.sql,jdk.crypto.ec,jdk.unsupported \
    --output "$BUILD_DIR/jre" \
    --compress=2 \
    --no-header-files \
    --no-man-pages

echo "Creating macOS app bundle..."

# Create app bundle structure
APP_BUNDLE="$BUILD_DIR/$APP_BUNDLE_NAME"
mkdir -p "$APP_BUNDLE/Contents/MacOS"
mkdir -p "$APP_BUNDLE/Contents/Resources"
mkdir -p "$APP_BUNDLE/Contents/PlugIns"
mkdir -p "$APP_BUNDLE/Contents/lib"

# Copy JRE
cp -R "$BUILD_DIR/jre" "$APP_BUNDLE/Contents/PlugIns/"

# Copy JARs
cp "$BUILD_DIR"/*.jar "$APP_BUNDLE/Contents/lib/"

# Create Info.plist
cat > "$APP_BUNDLE/Contents/Info.plist" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key>
    <string>$APP_NAME</string>
    <key>CFBundleDisplayName</key>
    <string>$APP_NAME</string>
    <key>CFBundleIdentifier</key>
    <string>$BUNDLE_ID</string>
    <key>CFBundleVersion</key>
    <string>$APP_VERSION</string>
    <key>CFBundleShortVersionString</key>
    <string>$APP_VERSION</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleSignature</key>
    <string>????</string>
    <key>CFBundleExecutable</key>
    <string>materia-tools</string>
    <key>CFBundleIconFile</key>
    <string>app-icon.icns</string>
    <key>NSPrincipalClass</key>
    <string>NSApplication</string>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>NSRequiresAquaSystemAppearance</key>
    <false/>
    <key>NSHumanReadableCopyright</key>
    <string>© 2025 Materia Project</string>
    <key>LSMinimumSystemVersion</key>
    <string>10.15</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
</dict>
</plist>
EOF

# Create launcher script
cat > "$APP_BUNDLE/Contents/MacOS/materia-tools" << 'EOF'
#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_ROOT="$SCRIPT_DIR/.."
JAVA_HOME="$APP_ROOT/PlugIns/jre"

# Set working directory to Resources
cd "$APP_ROOT/Resources"

# Launch the application
exec "$JAVA_HOME/bin/java" \
    -Xmx2g \
    -Dapple.awt.application.name="Materia Tools" \
    -Dcom.apple.macos.useScreenMenuBar=true \
    -Dapple.laf.useScreenMenuBar=true \
    -jar "$APP_ROOT/lib/materia-api-server.jar" \
    "$@"
EOF

chmod +x "$APP_BUNDLE/Contents/MacOS/materia-tools"

# Copy resources and icon
if [ -f "$RESOURCES_DIR/app-icon.icns" ]; then
    cp "$RESOURCES_DIR/app-icon.icns" "$APP_BUNDLE/Contents/Resources/"
else
    echo "Warning: app-icon.icns not found in resources directory"
fi

if [ -d "$RESOURCES_DIR" ]; then
    cp -R "$RESOURCES_DIR"/* "$APP_BUNDLE/Contents/Resources/" 2>/dev/null || true
fi

echo "Creating additional launcher scripts..."

# Create individual tool launchers
cat > "$APP_BUNDLE/Contents/MacOS/scene-editor" << 'EOF'
#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_ROOT="$SCRIPT_DIR/.."
JAVA_HOME="$APP_ROOT/PlugIns/jre"

exec "$JAVA_HOME/bin/java" \
    -Xmx1g \
    -Dapple.awt.application.name="Scene Editor" \
    -jar "$APP_ROOT/lib/editor-desktop.jar" \
    "$@"
EOF

cat > "$APP_BUNDLE/Contents/MacOS/profiler" << 'EOF'
#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_ROOT="$SCRIPT_DIR/.."
JAVA_HOME="$APP_ROOT/PlugIns/jre"

exec "$JAVA_HOME/bin/java" \
    -Xmx1g \
    -Dapple.awt.application.name="Performance Profiler" \
    -jar "$APP_ROOT/lib/profiler-desktop.jar" \
    "$@"
EOF

chmod +x "$APP_BUNDLE/Contents/MacOS/scene-editor"
chmod +x "$APP_BUNDLE/Contents/MacOS/profiler"

echo "Creating DMG installer..."

# Create temporary DMG mount point
DMG_TEMP="$BUILD_DIR/dmg-temp"
mkdir -p "$DMG_TEMP"

# Copy app bundle to DMG staging area
cp -R "$APP_BUNDLE" "$DMG_TEMP/"

# Create Applications symlink
ln -s /Applications "$DMG_TEMP/Applications"

# Create background and styling
if [ -f "$RESOURCES_DIR/dmg-background.png" ]; then
    mkdir -p "$DMG_TEMP/.background"
    cp "$RESOURCES_DIR/dmg-background.png" "$DMG_TEMP/.background/"
fi

# Create DMG
DMG_NAME="Materia-Tools-macOS.dmg"
hdiutil create -volname "$APP_NAME" \
    -srcfolder "$DMG_TEMP" \
    -ov \
    -format UDZO \
    -imagekey zlib-level=9 \
    "$DIST_DIR/$DMG_NAME"

echo "Code signing (if certificates available)..."

# Code signing (optional, requires developer certificates)
if [ -n "$DEVELOPER_ID" ]; then
    echo "Signing app bundle..."
    codesign --force --deep --sign "$DEVELOPER_ID" "$APP_BUNDLE"

    echo "Signing DMG..."
    codesign --force --sign "$DEVELOPER_ID" "$DIST_DIR/$DMG_NAME"

    echo "Notarizing (requires Apple ID)..."
    if [ -n "$APPLE_ID" ] && [ -n "$APPLE_ID_PASSWORD" ]; then
        xcrun notarytool submit "$DIST_DIR/$DMG_NAME" \
            --apple-id "$APPLE_ID" \
            --password "$APPLE_ID_PASSWORD" \
            --team-id "$TEAM_ID" \
            --wait

        xcrun stapler staple "$DIST_DIR/$DMG_NAME"
    fi
else
    echo "No signing certificate found. App will not be signed."
    echo "Set DEVELOPER_ID environment variable to enable signing."
fi

echo "Creating ZIP archive for distribution..."

# Create ZIP for easy distribution
cd "$BUILD_DIR"
zip -r "$DIST_DIR/Materia-Tools-macOS.zip" "$APP_BUNDLE_NAME"

echo
echo "========================================"
echo "macOS Packaging completed!"
echo "========================================"
echo "App Bundle: $BUILD_DIR/$APP_BUNDLE_NAME"
echo "DMG Installer: $DIST_DIR/$DMG_NAME"
echo "ZIP Archive: $DIST_DIR/Materia-Tools-macOS.zip"
echo "========================================"

# Verify the app bundle
echo "Verifying app bundle..."
if [ -x "$APP_BUNDLE/Contents/MacOS/materia-tools" ]; then
    echo "✓ Main executable is present and executable"
else
    echo "✗ Main executable missing or not executable"
fi

if [ -f "$APP_BUNDLE/Contents/Info.plist" ]; then
    echo "✓ Info.plist is present"
else
    echo "✗ Info.plist missing"
fi

if [ -d "$APP_BUNDLE/Contents/PlugIns/jre" ]; then
    echo "✓ JRE is bundled"
else
    echo "✗ JRE missing"
fi

echo "Packaging verification completed."