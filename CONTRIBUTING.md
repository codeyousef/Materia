# Contributing to Materia

Thank you for your interest in contributing to Materia! This document provides guidelines and instructions for contributing.

## Getting Started

1. **Fork the repository** and clone your fork locally
2. **Set up the development environment:**
   - JDK 17+
   - Node.js 18+ (for JS target)
   - Android SDK API 34 (for Android target)
3. **Build the project:** `./gradlew build`
4. **Run tests:** `./gradlew test`

## Development Workflow

### Branch Naming

- `feature/description` - New features
- `fix/description` - Bug fixes
- `docs/description` - Documentation updates
- `refactor/description` - Code refactoring

### Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use 4-space indentation
- Add KDoc comments for public APIs
- Keep functions focused and small
- Prefer explicit visibility modifiers

### Testing Requirements

- Add unit tests for new functionality
- Ensure existing tests pass: `./gradlew test`
- Maintain minimum 50% code coverage: `./gradlew koverVerify`

### Quality Gates

Before submitting a PR, run all quality checks:

```bash
./gradlew build              # Compile all targets
./gradlew test               # Run all tests
./gradlew koverVerify        # Check coverage
./gradlew lintDebug          # Android lint (if applicable)
```

## Pull Request Process

1. **Create a feature branch** from `main`
2. **Make your changes** with clear, focused commits
3. **Update documentation** if needed
4. **Run quality gates** to ensure all checks pass
5. **Submit a pull request** with:
   - Clear title describing the change
   - Description of what and why
   - Link to related issues (if any)
   - Screenshots for UI changes

### PR Review

- PRs require at least one approval before merging
- Address review feedback promptly
- Keep discussions constructive and professional

## Reporting Issues

When reporting bugs, please include:

- Materia version and platform (JVM/JS/Android)
- Steps to reproduce
- Expected vs actual behavior
- Relevant logs or error messages
- System information (OS, GPU, browser if applicable)

## Code of Conduct

- Be respectful and inclusive
- Focus on constructive feedback
- Help others learn and grow
- No harassment or discrimination

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).

## Questions?

Open a GitHub Discussion or Issue if you have questions about contributing.
