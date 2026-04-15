# Apollo Skills Guide

## Debugging Android Apps

**Quick check:**
```bash
./gradlew :android:apolloui:assembleLocalDebug
adb logcat | grep "apollo"
adb shell am start -n io.muun.apollo.debug.local/io.muun.apollo.presentation.ui.home.HomeActivity
```

**Common issues:**
- Libwallet not built → Run `./tools/libwallet-android.sh` first
- Houston not running → Check `docker compose ps houston`
- Certificate pinning fails → Use correct flavor (local/regtest/prod)

## Testing Patterns

**Pure tests (fast):**
```bash
./gradlew :android:apolloui:test
```

**Reference:** @android/apolloui/src/test/ for test structure

## Code Review Checklist

- [ ] No BaseActivity/BaseFragment/BasePresenter usage
- [ ] No ButterKnife (@BindView)
- [ ] No P2P/Contacts code added
- [ ] Kotlin (not Java) for new code
- [ ] ViewBinding used (not findViewById)
- [ ] MVVM pattern for new screens
- [ ] DB migration added if schema changed (@data/db/migrations/)
- [ ] Strings added to values/strings.xml AND values-es/strings.xml
- [ ] No security vulnerabilities (SQL injection, XSS, command injection)

## Pre-commit Hook

**What it does:**
- Runs automatically on `git commit`
- Lints only staged files (Python linter in @linters/pre-commit-linter.py)
- Checks: Checkstyle (Java/Kotlin), flake8 (Python), JSON, Dockerfile, Rust, custom linters
- Uses temporary commits to isolate staged changes, then reverts

**Checkstyle checks (Java/Kotlin):**
- UnusedImports (catches unused imports automatically)
- Line length (100 chars max)
- Whitespace, braces, indentation
- Modifier order, naming conventions
- Config: @linters/checkstyle/config.xml

**If pre-commit fails:**
- Fix the reported issues
- Re-stage changed files: `git add <files>`
- Commit again

## Refactoring Legacy Code

**DO:**
- Keep existing MVP screens as MVP (don't mix patterns)
- Extract logic to domain actions when possible
- Add tests before refactoring
- Use @Deprecated annotation with migration path

**DON'T:**
- Refactor BaseActivity screens to ExtensibleActivity (both deprecated)
- Mix MVP + MVVM in same screen
- Remove P2P code (will be removed in coordinated effort)

## Performance Optimization

**Check:**
- @android/apolloui/src/main/java/io/muun/apollo/presentation/ui/adapter/ - RecyclerView patterns
- Use ViewHolder pattern, avoid nested RecyclerViews
- Lazy load images with Picasso
- Use SQLDelight queries efficiently (avoid N+1)

**Profile:**
```bash
./gradlew :android:apolloui:assembleLocalDebug
# Android Studio → Profiler → CPU/Memory
```

## Security Considerations

**CRITICAL:**
- NEVER log sensitive data (keys, seeds, passwords)
- NEVER commit .env files or credentials
- Always validate user input before database queries
- Use HTTPS for all network calls (certificate pinning configured per flavor)
- Check @android/apolloui/src/main/java/io/muun/apollo/domain/libwallet/ for crypto operations
