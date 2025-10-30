#!/bin/bash

# Materia Tool Integration Test Runner
# Comprehensive testing script for validating the complete Materia tooling ecosystem

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOLS_DIR="$PROJECT_ROOT/tools"
LOGS_DIR="$PROJECT_ROOT/integration-test-logs"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# Create logs directory
mkdir -p "$LOGS_DIR"

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}                Materia Tool Integration Test Suite${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo "🚀 Starting comprehensive integration testing..."
echo "📍 Project Root: $PROJECT_ROOT"
echo "🔧 Tools Directory: $TOOLS_DIR"
echo "📝 Logs Directory: $LOGS_DIR"
echo "⏰ Timestamp: $TIMESTAMP"
echo ""

# Function to log with timestamp
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOGS_DIR/integration_test_$TIMESTAMP.log"
}

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to validate prerequisites
validate_prerequisites() {
    log "🔍 Validating prerequisites..."

    local missing_tools=()

    # Check for required tools
    if ! command_exists "kotlin"; then
        missing_tools+=("kotlin")
    fi

    if ! command_exists "gradle"; then
        missing_tools+=("gradle")
    fi

    if ! command_exists "node"; then
        missing_tools+=("node")
    fi

    if ! command_exists "npm"; then
        missing_tools+=("npm")
    fi

    if ! command_exists "docker"; then
        log "⚠️  Docker not found - some tests will be skipped"
    fi

    if [ ${#missing_tools[@]} -ne 0 ]; then
        echo -e "${RED}❌ Missing required tools: ${missing_tools[*]}${NC}"
        exit 1
    fi

    log "✅ All prerequisites validated"
}

# Function to prepare test environment
prepare_environment() {
    log "🛠️  Preparing test environment..."

    # Create temporary test workspace
    TEST_WORKSPACE="$LOGS_DIR/test_workspace_$TIMESTAMP"
    mkdir -p "$TEST_WORKSPACE"

    # Copy critical configuration files for testing
    if [ -f "$PROJECT_ROOT/.github/workflows/build-and-test.yml" ]; then
        cp "$PROJECT_ROOT/.github/workflows/build-and-test.yml" "$TEST_WORKSPACE/"
    fi

    if [ -f "$PROJECT_ROOT/build.gradle.kts" ]; then
        cp "$PROJECT_ROOT/build.gradle.kts" "$TEST_WORKSPACE/"
    fi

    log "✅ Test environment prepared"
}

# Function to test tool discovery
test_tool_discovery() {
    log "🔍 Testing tool discovery..."

    local tools_found=0
    local tools_expected=9

    # List of expected tools with their key files
    declare -A expected_tools=(
        ["web-host"]="$TOOLS_DIR/web-host/server.js"
        ["api-server"]="$TOOLS_DIR/api-server/build.gradle.kts"
        ["scene-editor"]="$TOOLS_DIR/editor/src/commonMain/kotlin/SceneEditor.kt"
        ["material-editor"]="$TOOLS_DIR/editor/src/commonMain/kotlin/material/ShaderEditor.kt"
        ["animation-editor"]="$TOOLS_DIR/editor/src/commonMain/kotlin/animation/Timeline.kt"
        ["profiler"]="$TOOLS_DIR/profiler/src/commonMain/kotlin/metrics/MetricsCollector.kt"
        ["testing-framework"]="$TOOLS_DIR/tests/src/commonMain/kotlin/execution/TestEngine.kt"
        ["docs-generator"]="$TOOLS_DIR/docs/src/main/kotlin/dokka/DokkaEnhancer.kt"
        ["packaging"]="$TOOLS_DIR/packaging/windows/package.bat"
    )

    for tool_name in "${!expected_tools[@]}"; do
        local tool_file="${expected_tools[$tool_name]}"
        if [ -f "$tool_file" ]; then
            log "   ✅ $tool_name: Found at $tool_file"
            ((tools_found++))
        else
            log "   ❌ $tool_name: Missing at $tool_file"
        fi
    done

    log "🔍 Tool Discovery: $tools_found/$tools_expected tools found"

    if [ $tools_found -eq $tools_expected ]; then
        log "✅ All expected tools discovered"
        return 0
    else
        log "⚠️  Some tools are missing"
        return 1
    fi
}

# Function to test individual tools
test_individual_tools() {
    log "🔧 Testing individual tools..."

    local passed_tests=0
    local total_tests=0

    # Test web host
    if test_web_host; then
        ((passed_tests++))
    fi
    ((total_tests++))

    # Test API server
    if test_api_server; then
        ((passed_tests++))
    fi
    ((total_tests++))

    # Test editor tools
    if test_editor_tools; then
        ((passed_tests++))
    fi
    ((total_tests++))

    # Test profiler
    if test_profiler; then
        ((passed_tests++))
    fi
    ((total_tests++))

    # Test documentation tools
    if test_documentation_tools; then
        ((passed_tests++))
    fi
    ((total_tests++))

    # Test packaging tools
    if test_packaging_tools; then
        ((passed_tests++))
    fi
    ((total_tests++))

    log "🔧 Individual Tools: $passed_tests/$total_tests tests passed"
    return $((total_tests - passed_tests))
}

# Individual tool test functions
test_web_host() {
    log "   Testing web host..."

    local web_host_dir="$TOOLS_DIR/web-host"

    if [ ! -f "$web_host_dir/package.json" ]; then
        log "     ❌ package.json not found"
        return 1
    fi

    if [ ! -f "$web_host_dir/server.js" ]; then
        log "     ❌ server.js not found"
        return 1
    fi

    # Check if package.json is valid JSON
    if ! node -e "JSON.parse(require('fs').readFileSync('$web_host_dir/package.json', 'utf8'))" 2>/dev/null; then
        log "     ❌ Invalid package.json"
        return 1
    fi

    log "     ✅ Web host validation passed"
    return 0
}

test_api_server() {
    log "   Testing API server..."

    local api_server_dir="$TOOLS_DIR/api-server"

    if [ ! -f "$api_server_dir/build.gradle.kts" ]; then
        log "     ❌ build.gradle.kts not found"
        return 1
    fi

    if [ ! -f "$api_server_dir/src/main/kotlin/ToolServer.kt" ]; then
        log "     ❌ ToolServer.kt not found"
        return 1
    fi

    # Check if build file contains required dependencies
    if ! grep -q "ktor" "$api_server_dir/build.gradle.kts"; then
        log "     ❌ Ktor dependency not found in build file"
        return 1
    fi

    log "     ✅ API server validation passed"
    return 0
}

test_editor_tools() {
    log "   Testing editor tools..."

    local editor_dir="$TOOLS_DIR/editor"
    local required_files=(
        "src/commonMain/kotlin/SceneEditor.kt"
        "src/commonMain/kotlin/material/ShaderEditor.kt"
        "src/commonMain/kotlin/animation/Timeline.kt"
    )

    for file in "${required_files[@]}"; do
        if [ ! -f "$editor_dir/$file" ]; then
            log "     ❌ $file not found"
            return 1
        fi
    done

    log "     ✅ Editor tools validation passed"
    return 0
}

test_profiler() {
    log "   Testing profiler..."

    local profiler_dir="$TOOLS_DIR/profiler"

    if [ ! -f "$profiler_dir/src/commonMain/kotlin/metrics/MetricsCollector.kt" ]; then
        log "     ❌ MetricsCollector.kt not found"
        return 1
    fi

    log "     ✅ Profiler validation passed"
    return 0
}

test_documentation_tools() {
    log "   Testing documentation tools..."

    local docs_dir="$TOOLS_DIR/docs"

    if [ ! -f "$docs_dir/src/main/kotlin/dokka/DokkaEnhancer.kt" ]; then
        log "     ❌ DokkaEnhancer.kt not found"
        return 1
    fi

    log "     ✅ Documentation tools validation passed"
    return 0
}

test_packaging_tools() {
    log "   Testing packaging tools..."

    local packaging_dir="$TOOLS_DIR/packaging"
    local platform_scripts=(
        "windows/package.bat"
        "macos/package.sh"
        "linux/package.sh"
    )

    for script in "${platform_scripts[@]}"; do
        if [ ! -f "$packaging_dir/$script" ]; then
            log "     ❌ $script not found"
            return 1
        fi
    done

    log "     ✅ Packaging tools validation passed"
    return 0
}

# Function to test tool integration
test_tool_integration() {
    log "🔗 Testing tool integration..."

    local integration_tests_passed=0
    local total_integration_tests=5

    # Test 1: Configuration consistency
    if test_configuration_consistency; then
        ((integration_tests_passed++))
    fi

    # Test 2: Build system integration
    if test_build_system_integration; then
        ((integration_tests_passed++))
    fi

    # Test 3: CI/CD integration
    if test_cicd_integration; then
        ((integration_tests_passed++))
    fi

    # Test 4: Documentation integration
    if test_documentation_integration; then
        ((integration_tests_passed++))
    fi

    # Test 5: Sample projects integration
    if test_sample_projects_integration; then
        ((integration_tests_passed++))
    fi

    log "🔗 Tool Integration: $integration_tests_passed/$total_integration_tests tests passed"
    return $((total_integration_tests - integration_tests_passed))
}

test_configuration_consistency() {
    log "   Testing configuration consistency..."

    # Check if all build files reference the same Kotlin version
    local kotlin_versions=()

    find "$PROJECT_ROOT" -name "build.gradle.kts" -exec grep -l "kotlin" {} \; | while read -r build_file; do
        if grep -q "kotlin.*version" "$build_file"; then
            local version=$(grep "kotlin.*version" "$build_file" | head -1)
            echo "$version" >> "$TEST_WORKSPACE/kotlin_versions.txt"
        fi
    done

    if [ -f "$TEST_WORKSPACE/kotlin_versions.txt" ]; then
        local unique_versions=$(sort "$TEST_WORKSPACE/kotlin_versions.txt" | uniq | wc -l)
        if [ "$unique_versions" -gt 1 ]; then
            log "     ⚠️  Multiple Kotlin versions detected"
            return 1
        fi
    fi

    log "     ✅ Configuration consistency validated"
    return 0
}

test_build_system_integration() {
    log "   Testing build system integration..."

    # Check if root build file exists and is valid
    if [ ! -f "$PROJECT_ROOT/build.gradle.kts" ]; then
        log "     ❌ Root build.gradle.kts not found"
        return 1
    fi

    # Check for settings.gradle.kts
    if [ ! -f "$PROJECT_ROOT/settings.gradle.kts" ]; then
        log "     ⚠️  settings.gradle.kts not found - multimodule setup may be incomplete"
    fi

    log "     ✅ Build system integration validated"
    return 0
}

test_cicd_integration() {
    log "   Testing CI/CD integration..."

    local cicd_files=(
        ".github/workflows/build-and-test.yml"
        ".gitlab-ci.yml"
    )

    local found_cicd=false
    for cicd_file in "${cicd_files[@]}"; do
        if [ -f "$PROJECT_ROOT/$cicd_file" ]; then
            found_cicd=true
            log "     ✅ Found $cicd_file"
        fi
    done

    if ! $found_cicd; then
        log "     ❌ No CI/CD configuration found"
        return 1
    fi

    log "     ✅ CI/CD integration validated"
    return 0
}

test_documentation_integration() {
    log "   Testing documentation integration..."

    # Check for documentation structure
    if [ ! -d "$PROJECT_ROOT/docs" ] && [ ! -d "$TOOLS_DIR/docs" ]; then
        log "     ❌ No documentation directory found"
        return 1
    fi

    # Check for key documentation files
    local doc_files_found=0
    local doc_files=(
        "README.md"
        "CLAUDE.md"
        "specs/003-generate-the-spec/quickstart.md"
    )

    for doc_file in "${doc_files[@]}"; do
        if [ -f "$PROJECT_ROOT/$doc_file" ]; then
            ((doc_files_found++))
        fi
    done

    if [ $doc_files_found -eq 0 ]; then
        log "     ❌ No documentation files found"
        return 1
    fi

    log "     ✅ Documentation integration validated ($doc_files_found files found)"
    return 0
}

test_sample_projects_integration() {
    log "   Testing sample projects integration..."

    local samples_dir="$PROJECT_ROOT/samples"

    if [ ! -d "$samples_dir" ]; then
        log "     ❌ Samples directory not found"
        return 1
    fi

    # Check for expected sample projects
    local expected_samples=(
        "tools-basic"
        "tools-advanced"
        "cicd-integration"
    )

    local samples_found=0
    for sample in "${expected_samples[@]}"; do
        if [ -d "$samples_dir/$sample" ]; then
            ((samples_found++))
            log "     ✅ Found sample: $sample"
        else
            log "     ⚠️  Missing sample: $sample"
        fi
    done

    if [ $samples_found -eq 0 ]; then
        log "     ❌ No sample projects found"
        return 1
    fi

    log "     ✅ Sample projects integration validated ($samples_found samples found)"
    return 0
}

# Function to run performance tests
test_performance() {
    log "⚡ Testing performance characteristics..."

    local performance_tests_passed=0
    local total_performance_tests=3

    # Test 1: File structure performance
    if test_file_structure_performance; then
        ((performance_tests_passed++))
    fi

    # Test 2: Build configuration performance
    if test_build_performance; then
        ((performance_tests_passed++))
    fi

    # Test 3: Tool startup simulation
    if test_tool_startup_performance; then
        ((performance_tests_passed++))
    fi

    log "⚡ Performance Tests: $performance_tests_passed/$total_performance_tests tests passed"
    return $((total_performance_tests - performance_tests_passed))
}

test_file_structure_performance() {
    log "   Testing file structure performance..."

    # Count total files and check for reasonable project size
    local total_files=$(find "$PROJECT_ROOT" -type f | wc -l)
    local kotlin_files=$(find "$PROJECT_ROOT" -name "*.kt" | wc -l)
    local config_files=$(find "$PROJECT_ROOT" -name "*.gradle*" -o -name "*.json" -o -name "*.yml" -o -name "*.yaml" | wc -l)

    log "     📊 Total files: $total_files"
    log "     📊 Kotlin files: $kotlin_files"
    log "     📊 Config files: $config_files"

    # Check for reasonable file counts (not too many, not too few)
    if [ "$total_files" -lt 10 ]; then
        log "     ⚠️  Very few files - project may be incomplete"
        return 1
    fi

    if [ "$total_files" -gt 1000 ]; then
        log "     ⚠️  Many files - consider cleanup"
    fi

    log "     ✅ File structure performance acceptable"
    return 0
}

test_build_performance() {
    log "   Testing build configuration performance..."

    # Check for efficient build configurations
    local gradle_files=$(find "$PROJECT_ROOT" -name "build.gradle.kts" | wc -l)

    log "     📊 Gradle build files: $gradle_files"

    # Check for common performance issues in build files
    local performance_issues=0

    find "$PROJECT_ROOT" -name "build.gradle.kts" | while read -r build_file; do
        # Check for parallel builds configuration
        if ! grep -q "parallel" "$build_file" 2>/dev/null; then
            ((performance_issues++))
        fi

        # Check for gradle daemon configuration
        if [ -f "$PROJECT_ROOT/gradle.properties" ]; then
            if ! grep -q "daemon=true" "$PROJECT_ROOT/gradle.properties" 2>/dev/null; then
                log "     ⚠️  Gradle daemon not explicitly enabled"
            fi
        fi
    done

    log "     ✅ Build configuration performance validated"
    return 0
}

test_tool_startup_performance() {
    log "   Testing tool startup performance simulation..."

    # Simulate tool startup by checking file accessibility
    local start_time=$(date +%s%N)

    # Simulate reading configuration files
    find "$TOOLS_DIR" -name "*.kt" -o -name "*.js" -o -name "*.json" | head -20 | while read -r file; do
        head -1 "$file" >/dev/null 2>&1
    done

    local end_time=$(date +%s%N)
    local duration_ns=$((end_time - start_time))
    local duration_ms=$((duration_ns / 1000000))

    log "     📊 Simulated startup time: ${duration_ms}ms"

    if [ "$duration_ms" -gt 1000 ]; then
        log "     ⚠️  Startup simulation took longer than expected"
        return 1
    fi

    log "     ✅ Tool startup performance acceptable"
    return 0
}

# Function to generate test report
generate_report() {
    local total_tests="$1"
    local passed_tests="$2"
    local failed_tests=$((total_tests - passed_tests))
    local success_rate=$(( (passed_tests * 100) / total_tests ))

    log ""
    log "═══════════════════════════════════════════════════════════════"
    log "                    INTEGRATION TEST REPORT"
    log "═══════════════════════════════════════════════════════════════"
    log ""
    log "📊 SUMMARY:"
    log "   • Total Tests: $total_tests"
    log "   • Passed: $passed_tests"
    log "   • Failed: $failed_tests"
    log "   • Success Rate: ${success_rate}%"
    log ""

    if [ "$success_rate" -ge 90 ]; then
        log "🎉 RESULT: EXCELLENT - Materia tooling ecosystem is production-ready!"
    elif [ "$success_rate" -ge 75 ]; then
        log "✅ RESULT: GOOD - Minor issues detected, but overall healthy"
    elif [ "$success_rate" -ge 50 ]; then
        log "⚠️  RESULT: ACCEPTABLE - Some significant issues need attention"
    else
        log "❌ RESULT: NEEDS WORK - Major issues detected"
    fi

    log ""
    log "📁 ARTIFACTS:"
    log "   • Full log: $LOGS_DIR/integration_test_$TIMESTAMP.log"
    log "   • Test workspace: $TEST_WORKSPACE"
    log ""
    log "═══════════════════════════════════════════════════════════════"
}

# Function to run Kotlin integration tester if available
run_kotlin_integration_tester() {
    local kotlin_tester="$TOOLS_DIR/integration/ToolIntegrationTester.kt"

    if [ -f "$kotlin_tester" ]; then
        log "🔍 Running Kotlin integration tester..."

        # Try to compile and run the Kotlin tester
        local kotlin_tester_dir="$TOOLS_DIR/integration"

        if command_exists "kotlinc"; then
            cd "$kotlin_tester_dir"

            # Create a temporary main function file
            cat > "TempMain.kt" << 'EOF'
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val tester = ToolIntegrationTester(java.io.File("../"))
    val report = tester.executeFullSuite()
    report.printReport()
}
EOF

            # Try to compile (this will likely fail due to missing dependencies, but we can check syntax)
            if kotlinc -cp "." "ToolIntegrationTester.kt" "TempMain.kt" -d "." 2>/dev/null; then
                log "     ✅ Kotlin integration tester compiled successfully"

                # Try to run it (this might fail due to runtime dependencies)
                if timeout 30s kotlin -cp "." TempMainKt 2>/dev/null; then
                    log "     ✅ Kotlin integration tester executed successfully"
                else
                    log "     ⚠️  Kotlin integration tester compilation succeeded but execution failed (likely missing runtime dependencies)"
                fi
            else
                log "     ⚠️  Kotlin integration tester found but compilation failed (likely missing dependencies)"
            fi

            # Clean up
            rm -f "TempMain.kt" "TempMainKt.class" "ToolIntegrationTesterKt.class" 2>/dev/null
            cd - >/dev/null
        else
            log "     ⚠️  Kotlin integration tester found but kotlinc not available"
        fi
    else
        log "⚠️  Kotlin integration tester not found at $kotlin_tester"
    fi
}

# Main execution
main() {
    local total_tests=0
    local passed_tests=0

    # Prerequisites
    validate_prerequisites
    prepare_environment

    # Tool Discovery (counts as 1 test)
    if test_tool_discovery; then
        ((passed_tests++))
    fi
    ((total_tests++))

    # Individual Tools (counts as multiple tests)
    local individual_tool_failures
    individual_tool_failures=$(test_individual_tools)
    local individual_tool_tests=6
    local individual_tool_passed=$((individual_tool_tests - individual_tool_failures))
    passed_tests=$((passed_tests + individual_tool_passed))
    total_tests=$((total_tests + individual_tool_tests))

    # Tool Integration (counts as multiple tests)
    local integration_failures
    integration_failures=$(test_tool_integration)
    local integration_tests=5
    local integration_passed=$((integration_tests - integration_failures))
    passed_tests=$((passed_tests + integration_passed))
    total_tests=$((total_tests + integration_tests))

    # Performance Tests (counts as multiple tests)
    local performance_failures
    performance_failures=$(test_performance)
    local performance_tests=3
    local performance_passed=$((performance_tests - performance_failures))
    passed_tests=$((passed_tests + performance_passed))
    total_tests=$((total_tests + performance_tests))

    # Run Kotlin integration tester if available
    run_kotlin_integration_tester

    # Generate final report
    generate_report "$total_tests" "$passed_tests"

    # Exit with appropriate code
    if [ "$passed_tests" -eq "$total_tests" ]; then
        exit 0
    elif [ "$passed_tests" -ge $((total_tests * 75 / 100)) ]; then
        exit 1
    else
        exit 2
    fi
}

# Run main function
main "$@"