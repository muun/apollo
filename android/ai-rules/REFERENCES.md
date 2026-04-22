# Apollo Reference Implementations

## MVVM Pattern

**ViewModel:**
```kotlin
class MyViewModel @Inject constructor() : ViewModel() {
    sealed interface ViewState {
        data class Data(val x: String) : ViewState
        data object Loading : ViewState
    }
    sealed interface ViewCommand { object Navigate : ViewCommand }

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState = _viewState.asStateFlow()
    private val _viewCommand = MutableSharedFlow<ViewCommand>(replay = 0)
    val viewCommand = _viewCommand.asSharedFlow()
}
```

**Activity:**
```kotlin
class MyActivity : AppCompatActivity() {
    private val binding by lazy { MyBinding.inflate(layoutInflater) }
    private val viewModel: MyViewModel by viewModels()
    @Inject lateinit var navigator: Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        (applicationContext as ApolloApplication).applicationComponent.activityComponent().inject(this)
        lifecycleScope.launch { repeatOnLifecycle(STARTED) { viewModel.viewState.collectLatest(::handleState) } }
    }
    private fun handleState(state: MyViewState) { when(state) { /* ... */ } }
}
```

**Real examples in codebase:**
- @android/apolloui/src/main/java/io/muun/apollo/presentation/ui/nfc/NfcReaderViewModel.kt

## AsyncAction Pattern

```kotlin
@Singleton
class FetchUserAction @Inject constructor(
    private val userRepository: UserRepository
) : AsyncAction<Unit, User>() {

    override fun action(params: Unit): Observable<User> {
        return userRepository.fetchUser()
    }
}
```

**Usage in ViewModel:**
```kotlin
class MyViewModel @Inject constructor(
    private val fetchUser: FetchUserAction
) : ViewModel() {

    fun loadUser() {
        viewModelScope.launch {
            fetchUser.run(Unit)
                .toFlow()
                .collectLatest { user ->
                    _viewState.value = ViewState.Data(user)
                }
        }
    }
}
```

**Real examples:**
- @domain/action/base/AsyncAction.java
- @domain/action/user/FetchUserAction.kt

## Database Migrations

**Creating a new migration:**
```sql
-- 39.sqm (next in sequence)
ALTER TABLE operation ADD COLUMN nfc_card_id TEXT;
CREATE INDEX idx_operation_nfc_card_id ON operation(nfc_card_id);
```

**Testing migration:**
```bash
./gradlew :apolloui:verifySqlDelightMigration
```

**Real examples:**
- @data/db/migrations/ (use next number in sequence)

## Custom Views

**MuunHeader setup:**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(binding.root)

    binding.header.apply {
        attachToActivity(this@onCreate)
        setNavigation(MuunHeader.Navigation.BACK)
        setTitle("My Screen")
    }
}
```

**Real examples:**
- @android/apolloui/src/main/java/io/muun/apollo/presentation/ui/view/MuunHeader.java

## Navigation

```kotlin
// Navigation handled by Activity

@Inject lateinit var navigator: Navigator

private fun navigateToNextScreen() {
    navigator.navigateToHome(this)
}
```

```kotlin
// Navigation handled by ViewModel

// In ViewModel - emit command
private val _viewCommand = MutableSharedFlow<ViewCommand>(replay = 0)
val viewCommand = _viewCommand.asSharedFlow()

fun onContinueClicked() {
    _viewCommand.tryEmit(ViewCommand.NavigateToHome)
}

// In Activity - handle command
private fun handleViewCommand(command: ViewCommand) {
    when (command) {
        ViewCommand.NavigateToHome -> {
            navigator.navigateToHome(this)
        }
    }
}
```

**Real examples:**
- @android/apolloui/src/main/java/io/muun/apollo/presentation/ui/utils/Navigator.kt
