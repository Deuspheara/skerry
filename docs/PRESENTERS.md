# Presenters

Screen logic lives in `:shared:presenters` (Kotlin Multiplatform, no UI, no `androidx.lifecycle`). Each UI (Compose TV today,
SwiftUI / Compose Desktop later) renders a presenter's state and sends it events.

## The pattern

```kotlin
class HomePresenter(
    repository: TwitchRepository,
    session: SessionRepository,
    snapshots: SnapshotCache?,
    scope: CoroutineScope,                 // the UI layer owns it and cancels it
    loadDispatcher: CoroutineDispatcher = Dispatchers.Default,   // only tests pass another one
) {
    val state: StateFlow<Load<HomeContent>>      // everything the screen shows
    fun onEvent(event: HomeEvent)                // everything the user can do
}
sealed interface HomeEvent { data object Retry : HomeEvent }
```

- One `state: StateFlow<UiState>` and one `onEvent(event)`; no other public mutating function. A state is an immutable data
  class (`ImmutableList` for lists). Network-backed screens expose `Load<T>` (`Loading`, `SignedOut`, `Failed`, `Ready`).
- A presenter never starts its own scope: the caller passes one, so the UI decides the lifetime (Android: a holder
  `ViewModel` passes `viewModelScope`; SwiftUI: an `ObservableObject` owns a `CoroutineScope` it cancels in `deinit`).
- Platform needs come in as interfaces from `:shared:core` / `:shared:presenters` (`DeviceProfile`, `VoiceInput`, repositories),
  never `Locale`, `Context` or `java.*`.
- Focus memory, scroll position and other pure UI state stay in the UI layer (e.g. `ChannelViewModel.lastClip`).

## Android wiring

`sharedPresentersModule()` registers one Koin `factory` per presenter, taking the scope as a parameter. The app's holders are
thin:

```kotlin
class HomeViewModel(create: (CoroutineScope) -> HomePresenter) : ViewModel() { val presenter = create(viewModelScope) }
// ViewModelModule: viewModel { HomeViewModel { scope -> get<HomePresenter> { parametersOf(scope) } } }
```

Screens collect `presenter.state` and call `presenter.onEvent(...)`. Navigation, back-stack scoping and configuration-change
survival are the holder's, as before.

**Registering a presenter:** add its factory to `sharedPresentersModule()` and keep the app's Koin start-up on
`productionModules()` (never a hand-written list): `ProductionGraphResolutionTest` constructs every screen ViewModel from that list.
A presenter module missing at runtime fails at the first `koinViewModel()`, and R8 then deletes the UI after the failing call.

## Testing

Presenter tests run in `commonTest` on the JVM and Android host: `runTest(UnconfinedTestDispatcher())`, `backgroundScope` as the
presenter's scope, the fakes of `:shared:testing` (`FakeRepository`, `FakeSession`, `FakeSettings`), and `loadDispatcher` /
`SnapshotCache(io = ...)` set to the test dispatcher so virtual time controls everything. `SharedPresentersModuleTest` resolves
every presenter against fakes of the platform bindings listed in `sharedPresentersModule()`'s KDoc.

## Compose stability

Shared modules are compiled without the Compose compiler. UI state classes that reach composable parameters must be listed in
`config/compose/stability.conf` (one by one, never the presenters). The compiler report
(`./gradlew :app:compileDebugKotlin -PcomposeReports`) must keep the same five unstable parameters.
