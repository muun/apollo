# CLAUDE.md

This directory contains Muun's self-custody wallet implementation for android (aka called Apollo).

# Apollo

## Overview
Apollo is the android app implementation of Muun's self-custody wallet for Bitcoin & Lightning. It enables users to send/receive btc over mainnet and lightning networks.

## Architecture

### Original architecture (what you will find in most of the codebase)
- **Architecture pattern**: Clean architecture (data/domain/presentation) & MVP (model-view-presenter)
- **Language**: Java
- **Reactive framework**: RxJava 1.X
- **Object oriented programming**: Heavy dependency on inheritance
- **UI framework**: Android view system (aka XMLs)

### Target architecture (where we are working towards)
- **Architecture pattern**: Clean architecture (data/domain/presentation) & MVVM (model-view-viewmodel)
- **Language**: Kotlin
- **Reactive framework**: RxJava 1.X
- **Object oriented programming**: Avoid inheritance hell (favor composition over inheritance), top-level/extension functions for sharable behaviour
- **UI framework**: Android view system (aka XMLs)

## Libwallet
Libwallet is a shared library written in Golang that contains domain logic shared between Apollo and Falcon (Muun's self-custody wallet implementation for iOS). Module :libwallet contains the .aar generated while building the libwallet project located at ../libwallet. Apollo & Libwallet communication is made through gRPC.

## Commands
```bash
./tools/libwallet-android.sh                    # MUST run before first build
./gradlew :android:apolloui:assembleLocalDebug
./gradlew :android:apolloui:assembleDogfoodDebug
```

## Token Economics

**CRITICAL**: All file operations must be token-efficient:

- **Be concise**: Remove verbosity, keep technical accuracy
- **Avoid repetition**: Don't repeat context already established
- **No code examples in CLAUDE.md**: All examples belong in REFERENCES.md
- **Direct communication**: Skip filler phrases
- **Efficient updates**: Only modify necessary sections
- **Prefer references**: Link to existing examples instead of duplicating

**Apply to all files**: Code, documentation, comments, commit messages, AI responses.

## Code Style
- IMPORTANT: All new code MUST be Kotlin (not Java)
- NEVER use ButterKnife (@BindView); prefer ViewBinding
- YOU MUST use MVVM for new screens (not MVP)
- ViewModels: StateFlow for continuous state, SharedFlow for one-time events

## Code Quality
- Pre-commit hook runs Checkstyle/linters on all commits (style, imports, line length, whitespace)
- YOU MUST fix all linter errors before committing (hook will fail otherwise)
- Common issues: unused imports, lines >100 chars, whitespace, brace style
- See @android/ai-rules/SKILLS.md for full pre-commit hook details and how to fix failures

## Documentation
- **SKILLS.md** - Debugging, testing, code review, refactoring, performance, security (@android/ai-rules/SKILLS.md)
- **LEARNINGS.md** - Common mistakes, deprecation history, codebase gotchas (@android/ai-rules/LEARNINGS.md)
- **FEEDBACK.md** - How to present code changes, errors, options, progress (@android/ai-rules/FEEDBACK.md)
- **REFERENCES.md** - MVVM, AsyncAction, migrations, custom views, RecyclerView, navigation (@android/ai-rules/REFERENCES.md)

YOU MUST **proactively** update these files when you discover new patterns, gotchas, or better presentation techniques — do it immediately as part of the task, don't wait to be asked. This is how knowledge persists across sessions.

## Critical: always read files completely

When instructed to read a file as input (e.g. deployment_input.md), read the ENTIRE file using multiple Read calls with offset/limit if needed. Never start analyzing until every line has been read.
