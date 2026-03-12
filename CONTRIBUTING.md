# Contributing to RouteSnap

Thank you for your interest in contributing to RouteSnap! This document provides guidelines and instructions for contributing.

## Getting Started

### Prerequisites
- Android Studio Arctic Fox or newer
- JDK 17
- Android SDK with API 34

### Setup
1. Fork the repository
2. Clone your fork: `git clone https://github.com/YOUR_USERNAME/routesnap.git`
3. Open in Android Studio
4. Sync Gradle files

## Development Workflow

### Branch Naming
- `feature/description` - New features
- `fix/description` - Bug fixes
- `docs/description` - Documentation updates
- `refactor/description` - Code refactoring

### Commit Messages
Follow [Conventional Commits](https://www.conventionalcommits.org/):
```
feat: add photo clustering algorithm
fix: resolve EXIF extraction crash
docs: update README with build instructions
```

### Pull Request Process
1. Create a branch from `develop`
2. Make your changes
3. Ensure all tests pass: `./gradlew test`
4. Run lint: `./gradlew lint`
5. Submit PR to `develop` branch
6. Wait for review

## Code Style

### Kotlin Guidelines
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Keep functions small and focused
- Add KDoc comments for public APIs

### Compose Guidelines
- Keep composables small and reusable
- Use `@Preview` for UI components
- Follow Material Design 3 guidelines

## Architecture

RouteSnap follows Clean Architecture principles:

```
app/
├── data/          # Data layer (repositories, data sources)
├── domain/        # Business logic (models, use cases)
├── ui/            # Presentation layer (Compose UI, ViewModels)
└── di/            # Dependency injection
```

## Testing

### Unit Tests
```kotlin
@Test
fun `clustering should group nearby photos`() {
    // Test implementation
}
```

### Run Tests
```bash
./gradlew test          # Run unit tests
./gradlew connectedCheck  # Run instrumented tests
```

## Building

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

## CI/CD

GitHub Actions automatically:
- Builds on every push to `main` and `develop`
- Runs tests and lint
- Creates releases on version tags (v*)

## Areas for Contribution

### High Priority
- [ ] Media3 Transformer video rendering implementation
- [ ] MapLibre map snapshot generation
- [ ] Canvas overlay for route animations
- [ ] Audio mixing and ducking
- [ ] Ken Burns effects for photos

### Medium Priority
- [ ] Video trimming functionality
- [ ] Music picker integration
- [ ] Share to Instagram/TikTok
- [ ] Performance optimizations

### Nice to Have
- [ ] Beat-synced editing
- [ ] 3D terrain visualization
- [ ] Cloud backup
- [ ] Collaborative trips

## Questions?

- Open an issue for bugs or feature requests
- Check existing issues before creating new ones
- Join discussions in existing issues

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
