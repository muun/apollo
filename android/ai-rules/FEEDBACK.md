# Apollo Feedback Guide

## Reporting Technical Decisions

**Format:**
```
I'm using [PATTERN] because [REASON].

Example implementation: [FILE] in [CLASS/FUNCTION]
```

**Example:**
```
I'm using StateFlow instead of LiveData because the codebase is migrating to Coroutines Flow.

Reference: NfcReaderViewModel.kt (viewState property)
```

## Presenting Code Changes

**DO:**
- Use file and function/class references (e.g., `MyActivity.kt in onCreate()`)
- Show only changed sections, not entire files
- Explain WHY, not just WHAT
- Link to reference implementations in codebase

**DON'T:**
- Dump entire file contents
- Explain basic Kotlin/Android concepts (assume user knows)
- Over-explain trivial changes

## Error Reporting

**Build errors:**
```
Build failed at [TASK]:
[ERROR MESSAGE]

Likely cause: [HYPOTHESIS]
Fix: [ACTION]
```

**Runtime errors:**
```
Crash in [ACTIVITY/VIEWMODEL]:
[STACK TRACE - relevant lines only]

Root cause: [ANALYSIS]
Fix: [CHANGES MADE]
```

## Asking for Clarification

**When unclear:**
- Architecture decision (MVVM vs MVP for specific case)
- UX behavior (navigation flow, error handling)
- Data model changes (affects DB schema)

**DON'T ask about:**
- Code style (follow existing patterns)
- Naming conventions (follow codebase conventions)

## Presenting Options

**Format:**
```
Option 1: [APPROACH]
Pros: [...]
Cons: [...]
Example: @path/to/reference

Option 2: [APPROACH]
Pros: [...]
Cons: [...]
Example: @path/to/reference

Recommendation: Option [X] because [REASON]
```

## Progress Updates

**For multi-step tasks:**
1. List steps upfront
2. Mark completed steps
3. Report blockers immediately
4. Summarize at end

**Example:**
```
Completed:
✓ Created ViewModel with ViewState/ViewCommand
✓ Created Activity with ViewBinding
✓ Added English strings

In progress:
→ Adding Spanish translations

Pending:
- Testing navigation flow
```
