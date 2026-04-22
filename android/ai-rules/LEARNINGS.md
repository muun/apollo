# Apollo Learnings & Gotchas

## Common Mistakes

**ViewState location:**
- ❌ Separate file: `sealed interface ViewState` in ViewState.kt
- ✅ Inside ViewModel: `sealed interface ViewState` inside MyViewModel.kt

**Activity base class:**
- ❌ Extending BaseActivity (deprecated)
- ❌ Extending ExtensibleActivity (deprecated)
- ✅ Extending AppCompatActivity directly

**View binding:**
- ❌ `private lateinit var binding: MyBinding`
- ✅ `private val binding by lazy { MyBinding.inflate(layoutInflater) }`

**ViewModel instantiation:**
- ❌ `private val viewModel = MyViewModel()` (no DI)
- ✅ `private val viewModel: MyViewModel by viewModels()` (Dagger)

**Flow collection:**
- ❌ `viewModel.state.collect {}` in onCreate (leaks)
- ✅ `lifecycleScope.launch { repeatOnLifecycle(STARTED) { viewModel.state.collectLatest {} } }`

**ButterKnife → ViewBinding migration:**
- Remove `tools:viewBindingIgnore="true"` from layout XML
- Remove `@BindView` annotations and butterknife imports
- Add `bindingInflater()` override returning lambda with `MyBinding::inflate`
- Keep `getLayoutResource()` returning 0 with TODO comment (abstract method, will be removed after full migration)
- Access views via `binding.viewId` instead of direct field reference
- DON'T remove `getLayoutResource()` (causes compile error, it's abstract in BaseFragment)

## Deprecation History

**Why BaseActivity is deprecated:**
- Massive god class with 50+ methods
- Tight coupling to MVP pattern
- Hard to test, hard to understand
- Blocks migration to MVVM

**Why P2P/Contacts is deprecated:**
- Feature never gained traction
- Maintenance burden too high
- Focus shifting to core wallet functionality
- Will be removed in Q2 2026

**Why ButterKnife is deprecated:**
- ViewBinding is official Android solution
- Better null safety, compile-time checking
- No reflection overhead

## Codebase Gotchas

**Flavor-specific behavior:**
- Certificate pins differ per flavor (@android/apolloui/houston.gradle)
- Local uses localhost:8080, regtest uses remote, prod uses api.muun.com
- DON'T hardcode URLs, use BuildConfig

**Database migrations:**
- SQLDelight migrations are NOT auto-applied
- MUST add migration in @data/db/migrations/ (next number in sequence)
- Test migration with `./gradlew :apolloui:verifySqlDelightMigration`

**Libwallet changes:**
- If you change @libwallet/, MUST rebuild: `./tools/libwallet-android.sh`
- Changes won't reflect until rebuilt and gradle sync'd
- Takes ~5 minutes to build

**MuunHeader:**
- Custom view for consistent header across app
- Use `binding.header.attachToActivity(this)` in Activity.onCreate
- Navigation modes: BACK, NONE, EXIT
- Reference: @android/apolloui/src/main/java/io/muun/apollo/presentation/ui/view/MuunHeader.java

## Crashlytics Investigation

**Always pull at least 10 events across all variants:**
- 2 events gave incomplete picture (ANR appeared low-end-only, but Pixel 7a was affected too)
- More events reveal patterns: device diversity, processState, OS version clustering
- Use `crashlytics_batch_get_events` with sample events from all variants, at least 10 total
- Prioritize events from different variants for maximum diversity

**Device fingerprint red flags:**
- `locale: UNSET` + `bigQueryPseudoId: null` → likely automated/non-legitimate environment
- `installSource-initiatingPackage: com.miui.huanji` → Xiaomi phone migration tool (copies SharedPreferences but NOT Android Keystore)

## Android/Kotlin Gotchas

**SharedFlow vs StateFlow:**
- StateFlow: Continuous state (always has current value)
- SharedFlow: One-time events (navigation, toasts, errors)
- DON'T use StateFlow for one-time events (will replay on config change)

**repeatOnLifecycle:**
- STARTED: Collect while visible (correct for most UI updates)
- RESUMED: Collect while focused (use for analytics)
- CREATED: Collect while alive (leaks, don't use)

**ViewBinding naming:**
- activity_my_screen.xml → ActivityMyScreenBinding
- fragment_my_screen.xml → FragmentMyScreenBinding
- view_my_widget.xml → ViewMyWidgetBinding

**Kotlin scope functions:**
- Use `apply` when calling multiple methods on same object (avoid repetition)
- ❌ `binding.header.attachToActivity(this); binding.header.setTitle(...)`
- ✅ `binding.header.apply { attachToActivity(this@onCreate); setTitle(...) }`
