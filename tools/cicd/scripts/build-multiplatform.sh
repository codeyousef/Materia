#!/bin/bash
# Materia Tools - Multi-Platform Build Script
# Builds and tests across all supported platforms

set -e

echo "========================================"
echo "Materia Tools - Multi-Platform Build"
echo "========================================"

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BUILD_DIR="$PROJECT_ROOT/build/multiplatform"
PLATFORMS=${PLATFORMS:-"jvm js android ios linuxX64 mingwX64 macosX64"}
GRADLE_OPTS=${GRADLE_OPTS:-"-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=4"}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Cleanup function
cleanup() {
    log_info "Cleaning up build processes..."
    pkill -f gradle || true
    pkill -f java || true
}

# Error handler
error_handler() {
    local line_number=$1
    log_error "Build failed at line $line_number"
    cleanup
    exit 1
}

trap 'error_handler $LINENO' ERR
trap cleanup EXIT

# Environment validation
validate_environment() {
    log_info "Validating build environment..."

    # Check Java version
    if ! command -v java &> /dev/null; then
        log_error "Java not found. Please install Java 17 or later."
        exit 1
    fi

    local java_version=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$java_version" -lt 17 ]; then
        log_error "Java 17 or later is required. Found: $java_version"
        exit 1
    fi

    # Check Gradle wrapper
    if [ ! -f "$PROJECT_ROOT/gradlew" ]; then
        log_error "Gradle wrapper not found at $PROJECT_ROOT/gradlew"
        exit 1
    fi

    # Check Android SDK for Android builds
    if echo "$PLATFORMS" | grep -q "android" && [ -z "$ANDROID_HOME" ]; then
        log_warning "ANDROID_HOME not set. Android builds may fail."
    fi

    # Check Xcode for iOS builds
    if echo "$PLATFORMS" | grep -q "ios" && ! command -v xcodebuild &> /dev/null; then
        log_warning "Xcode not found. iOS builds may fail."
    fi

    log_success "Environment validation completed"
}

# Clean build directories
clean_build() {
    log_info "Cleaning previous builds..."
    cd "$PROJECT_ROOT"
    ./gradlew clean
    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR"
    log_success "Build directories cleaned"
}

# Build core library for all platforms
build_core_library() {
    log_info "Building core library for platforms: $PLATFORMS"
    cd "$PROJECT_ROOT"

    local build_tasks=""
    for platform in $PLATFORMS; do
        case $platform in
            jvm)
                build_tasks="$build_tasks jvmJar"
                ;;
            js)
                build_tasks="$build_tasks jsJar"
                ;;
            android)
                build_tasks="$build_tasks assembleRelease"
                ;;
            ios)
                build_tasks="$build_tasks iosArm64MainKlibrary iosSimulatorArm64MainKlibrary"
                ;;
            linuxX64)
                build_tasks="$build_tasks linuxX64MainKlibrary"
                ;;
            mingwX64)
                build_tasks="$build_tasks mingwX64MainKlibrary"
                ;;
            macosX64)
                build_tasks="$build_tasks macosX64MainKlibrary"
                ;;
        esac
    done

    if [ -n "$build_tasks" ]; then
        ./gradlew $build_tasks
        log_success "Core library built for all platforms"
    fi
}

# Build tools for supported platforms
build_tools() {
    log_info "Building development tools..."
    cd "$PROJECT_ROOT"

    # Desktop tools (JVM-based)
    if echo "$PLATFORMS" | grep -q "jvm"; then
        log_info "Building desktop tools..."
        ./gradlew :tools:editor:jar
        ./gradlew :tools:profiler:jar
        ./gradlew :tools:docs:jar
        ./gradlew :tools:api-server:shadowJar
        log_success "Desktop tools built successfully"
    fi

    # Web tools
    if echo "$PLATFORMS" | grep -q "js"; then
        log_info "Building web tools..."
        if [ -d "tools/web-host" ] && [ -f "tools/web-host/package.json" ]; then
            cd tools/web-host
            npm ci
            npm run build
            cd "$PROJECT_ROOT"
        fi

        if [ -d "tools/editor/web" ] && [ -f "tools/editor/web/package.json" ]; then
            cd tools/editor/web
            npm ci
            npm run build
            cd "$PROJECT_ROOT"
        fi
        log_success "Web tools built successfully"
    fi
}

# Run tests for all platforms
run_tests() {
    log_info "Running tests for platforms: $PLATFORMS"
    cd "$PROJECT_ROOT"

    local test_tasks=""
    local test_results="$BUILD_DIR/test-results"
    mkdir -p "$test_results"

    for platform in $PLATFORMS; do
        case $platform in
            jvm)
                test_tasks="$test_tasks jvmTest"
                ;;
            js)
                test_tasks="$test_tasks jsTest"
                ;;
            android)
                # Android tests require emulator or device
                if [ "$RUN_ANDROID_TESTS" = "true" ]; then
                    test_tasks="$test_tasks testDebugUnitTest"
                fi
                ;;
            linuxX64|mingwX64|macosX64)
                test_tasks="$test_tasks ${platform}Test"
                ;;
        esac
    done

    if [ -n "$test_tasks" ]; then
        ./gradlew $test_tasks --continue || {
            log_error "Some tests failed. Check test reports for details."
            # Don't exit on test failures if CONTINUE_ON_TEST_FAILURE is set
            if [ "$CONTINUE_ON_TEST_FAILURE" != "true" ]; then
                exit 1
            fi
        }
        log_success "Tests completed for all platforms"
    fi

    # Copy test results
    find . -name "TEST-*.xml" -exec cp {} "$test_results/" \; 2>/dev/null || true
}

# Package artifacts
package_artifacts() {
    log_info "Packaging build artifacts..."
    local artifacts_dir="$BUILD_DIR/artifacts"
    mkdir -p "$artifacts_dir"

    cd "$PROJECT_ROOT"

    # Core library artifacts
    find build/libs -name "*.jar" -exec cp {} "$artifacts_dir/" \; 2>/dev/null || true
    find tools -name "*.jar" -exec cp {} "$artifacts_dir/" \; 2>/dev/null || true

    # Web artifacts
    if [ -d "tools/web-host/dist" ]; then
        cp -r tools/web-host/dist "$artifacts_dir/web-host"
    fi
    if [ -d "tools/editor/web/dist" ]; then
        cp -r tools/editor/web/dist "$artifacts_dir/editor-web"
    fi

    # Native artifacts
    find build -name "*.klib" -exec cp {} "$artifacts_dir/" \; 2>/dev/null || true

    # Documentation
    if [ -d "build/dokka" ]; then
        cp -r build/dokka "$artifacts_dir/"
    fi

    log_success "Artifacts packaged in $artifacts_dir"
}

# Generate build report
generate_report() {
    log_info "Generating build report..."
    local report_file="$BUILD_DIR/build-report.json"

    cat > "$report_file" << EOF
{
  "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")",
  "platforms": [$(echo "$PLATFORMS" | sed 's/ /", "/g' | sed 's/^/"/' | sed 's/$/"/')],
  "environment": {
    "java_version": "$(java -version 2>&1 | head -1 | cut -d'"' -f2)",
    "gradle_version": "$(cd "$PROJECT_ROOT" && ./gradlew --version | grep "Gradle" | cut -d' ' -f2)",
    "os": "$(uname -s)",
    "arch": "$(uname -m)"
  },
  "build_status": "success",
  "artifacts": {
    "core_library": true,
    "desktop_tools": $(echo "$PLATFORMS" | grep -q "jvm" && echo "true" || echo "false"),
    "web_tools": $(echo "$PLATFORMS" | grep -q "js" && echo "true" || echo "false"),
    "native_libraries": $(echo "$PLATFORMS" | grep -qE "(linux|mingw|macos)" && echo "true" || echo "false")
  },
  "build_time_seconds": $SECONDS
}
EOF

    log_success "Build report generated: $report_file"
}

# Performance metrics
collect_metrics() {
    log_info "Collecting performance metrics..."
    local metrics_file="$BUILD_DIR/metrics.json"

    # Gradle build scan URL if available
    local build_scan_url=""
    if [ -f "$PROJECT_ROOT/build/reports/build-scan-url.txt" ]; then
        build_scan_url=$(cat "$PROJECT_ROOT/build/reports/build-scan-url.txt")
    fi

    cat > "$metrics_file" << EOF
{
  "build_duration_seconds": $SECONDS,
  "platforms_built": $(echo "$PLATFORMS" | wc -w),
  "build_scan_url": "$build_scan_url",
  "memory_usage": {
    "max_heap": "$(./gradlew --version | grep "Max. heap size" | cut -d' ' -f4 || echo "unknown")"
  },
  "parallel_builds": true,
  "cache_hits": "unknown"
}
EOF

    log_success "Performance metrics collected: $metrics_file"
}

# Main execution
main() {
    local start_time=$(date +%s)

    log_info "Starting multi-platform build process..."
    log_info "Target platforms: $PLATFORMS"
    log_info "Project root: $PROJECT_ROOT"
    log_info "Build directory: $BUILD_DIR"

    validate_environment
    clean_build
    build_core_library
    build_tools

    if [ "$SKIP_TESTS" != "true" ]; then
        run_tests
    else
        log_warning "Skipping tests (SKIP_TESTS=true)"
    fi

    package_artifacts
    collect_metrics
    generate_report

    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    echo
    echo "========================================"
    log_success "Multi-platform build completed successfully!"
    log_info "Total build time: ${duration}s"
    log_info "Artifacts location: $BUILD_DIR/artifacts"
    log_info "Build report: $BUILD_DIR/build-report.json"
    echo "========================================"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --platforms)
            PLATFORMS="$2"
            shift 2
            ;;
        --skip-tests)
            SKIP_TESTS="true"
            shift
            ;;
        --continue-on-test-failure)
            CONTINUE_ON_TEST_FAILURE="true"
            shift
            ;;
        --android-tests)
            RUN_ANDROID_TESTS="true"
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  --platforms PLATFORMS    Specify platforms to build (default: $PLATFORMS)"
            echo "  --skip-tests             Skip running tests"
            echo "  --continue-on-test-failure Continue build even if tests fail"
            echo "  --android-tests          Run Android tests (requires emulator)"
            echo "  --help                   Show this help message"
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Execute main function
main "$@"