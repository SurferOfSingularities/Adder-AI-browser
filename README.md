# Adder

AI-powered ad-blocking browser for Android and iOS using Kotlin Multiplatform and on-device LLMs.

## Project Documentation

This project contains detailed documentation to help developers and AI agents understand the architecture, tech stack, and conventions:

- [Project Purpose & Overview](docs/project.md)
- [Architecture Decisions](docs/architecture.md)
- [Tech Stack](docs/tech-stack.md)
- [Coding Conventions](docs/conventions.md)

## Project Structure

- `shared/`: Common Kotlin Multiplatform code (Business logic, UI via Compose Multiplatform).
- `androidApp/`: Android-specific application code and Gemini Nano integration.
- `iosApp/`: iOS-specific application code and Apple Foundation Models integration.

## IDE Agnostic Context

While this project was initialized with Amazon Kiro, it is designed to be IDE-agnostic. The documentation in the `docs/` directory is a mirror of the `.kiro/steering/` files to ensure that any development environment or AI assistant has full context of the project's goals and implementation details.

## Maintenance & Sync Requirements

**IMPORTANT for Developers and AI Agents:**
To maintain IDE-agnostic context, the documentation in `.kiro/steering/` and `docs/` **must always be kept in sync**.
- Any update to a file in `.kiro/steering/` must be mirrored to the corresponding file in `docs/`.
- Any update to a file in `docs/` must be mirrored to the corresponding file in `.kiro/steering/`.
- This ensures that both Kiro-based and standard AI assistants remain aligned on the project's architecture and conventions.
