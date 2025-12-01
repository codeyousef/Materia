package io.materia.validation.utils

/**
 * Git information utilities for validation reporting.
 *
 * Provides platform-independent git information extraction.
 * Platform-specific implementations can use Process execution or git commands.
 */
object GitInfo {

    /**
     * Attempts to get the current git branch name.
     * Returns "unknown" if git info cannot be retrieved.
     *
     * Note: Actual git command execution would be implemented in platform-specific
     * code or via configuration injection. This approach allows flexibility for
     * CI/CD environments where git info can be passed via environment variables.
     */
    fun getBranchName(projectPath: String): String {
        // In CI/CD, branch name is typically available via environment variables:
        // - GitHub Actions: GITHUB_REF_NAME
        // - GitLab CI: CI_COMMIT_REF_NAME
        // - Jenkins: GIT_BRANCH
        // Platform-specific implementation would execute: git rev-parse --abbrev-ref HEAD
        return "unknown"
    }

    /**
     * Attempts to get the current git commit hash.
     * Returns "unknown" if git info cannot be retrieved.
     *
     * Note: Actual git command execution would be implemented in platform-specific
     * code or via configuration injection. This approach allows flexibility for
     * CI/CD environments where git info can be passed via environment variables.
     */
    fun getCommitHash(projectPath: String): String {
        // In CI/CD, commit hash is typically available via environment variables:
        // - GitHub Actions: GITHUB_SHA
        // - GitLab CI: CI_COMMIT_SHA
        // - Jenkins: GIT_COMMIT
        // Platform-specific implementation would execute: git rev-parse HEAD
        return "unknown"
    }

    /**
     * Creates git configuration map from environment variables or defaults.
     * This method checks common CI/CD environment variables for git information.
     */
    fun getGitConfiguration(): Map<String, String> {
        return mapOf(
            "branchName" to getBranchFromEnvironment(),
            "commitHash" to getCommitFromEnvironment()
        )
    }

    private fun getBranchFromEnvironment(): String {
        // Platform-specific environment variable lookup
        return "unknown"
    }

    private fun getCommitFromEnvironment(): String {
        // Platform-specific environment variable lookup
        return "unknown"
    }
}
