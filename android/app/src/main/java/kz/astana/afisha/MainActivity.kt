package kz.astana.afisha

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kz.astana.afisha.data.SessionStore
import kz.astana.afisha.domain.AppLanguage
import kz.astana.afisha.ui.city.CitySelectScreen
import kz.astana.afisha.ui.detail.EventDetailScreen
import kz.astana.afisha.ui.feed.FeedScreen
import kz.astana.afisha.ui.language.LanguageScreen
import kz.astana.afisha.ui.onboarding.OnboardingScreen
import kz.astana.afisha.ui.settings.SettingsScreen
import kz.astana.afisha.ui.signin.SignInScreen
import kz.astana.afisha.ui.theme.AstanaAfishaTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            AstanaAfishaTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { AppRoot() }
            }
        }
    }
}

/** Applies [lang] as the per-app locale (no-op if already active). Activity is recreated by the system. */
fun applyAppLanguage(lang: AppLanguage) {
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    if (current != lang.tag) AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
}

private object Routes {
    const val Language = "language"
    const val LanguageFromSettings = "language_settings"
    const val City = "city"
    const val CityFromSettings = "city_settings"
    const val SignIn = "sign_in"
    const val Onboarding = "onboarding"
    const val Feed = "feed"
    const val Settings = "settings"
    const val Detail = "detail/{eventId}"
    fun detail(eventId: String) = "detail/${android.net.Uri.encode(eventId)}"
    /** savedStateHandle key: id hidden on the detail screen → feed shows "Hidden · Undo". */
    const val HiddenFromDetail = "hidden_from_detail"
}

@Composable
private fun AppRoot() {
    val ready by SessionStore.ready.collectAsState()
    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    // DataStore is the source of truth for the language: re-apply it if the system lost it.
    LaunchedEffect(Unit) { SessionStore.language.value?.let(::applyAppLanguage) }

    val nav = rememberNavController()
    fun nextAfterSetup(): String {
        val p = SessionStore.profile.value
        return when {
            SessionStore.language.value == null -> Routes.Language
            SessionStore.city.value == null -> Routes.City
            p == null -> Routes.SignIn
            !p.onboardingDone -> Routes.Onboarding
            else -> Routes.Feed
        }
    }
    val start = remember { nextAfterSetup() }
    fun advance(from: String) {
        nav.navigate(nextAfterSetup()) { popUpTo(from) { inclusive = true } }
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.Language) {
            LanguageScreen(onSelected = { lang ->
                SessionStore.setLanguage(lang)
                advance(Routes.Language)
                applyAppLanguage(lang)
            })
        }
        composable(Routes.LanguageFromSettings) {
            LanguageScreen(
                onSelected = { lang ->
                    SessionStore.setLanguage(lang)
                    nav.popBackStack()
                    applyAppLanguage(lang)
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.City) { CitySelectScreen(onSelected = { advance(Routes.City) }) }
        composable(Routes.CityFromSettings) {
            CitySelectScreen(onSelected = { nav.popBackStack() }, onBack = { nav.popBackStack() })
        }
        composable(Routes.SignIn) { SignInScreen(onSignedIn = { advance(Routes.SignIn) }) }
        composable(Routes.Onboarding) { OnboardingScreen(onDone = { advance(Routes.Onboarding) }) }
        composable(Routes.Feed) { entry ->
            val handle = entry.savedStateHandle
            val hiddenFromDetail by handle.getStateFlow<String?>(Routes.HiddenFromDetail, null).collectAsState()
            FeedScreen(
                onOpenSettings = { nav.navigate(Routes.Settings) },
                onChangeCity = { nav.navigate(Routes.CityFromSettings) },
                onOpenEvent = { id -> nav.navigate(Routes.detail(id)) { launchSingleTop = true } },
                hiddenFromDetail = hiddenFromDetail,
                onHiddenFromDetailShown = { handle[Routes.HiddenFromDetail] = null },
            )
        }
        composable(
            Routes.Detail,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
        ) { entry ->
            val eventId = entry.arguments?.getString("eventId").orEmpty()
            EventDetailScreen(
                eventId = eventId,
                onBack = { nav.popBackStack() },
                onHidden = { id ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(Routes.HiddenFromDetail, id)
                    nav.popBackStack()
                },
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onSignedOut = { nav.navigate(Routes.SignIn) { popUpTo(0) { inclusive = true } } },
                onChangeCity = { nav.navigate(Routes.CityFromSettings) },
                onChangeLanguage = { nav.navigate(Routes.LanguageFromSettings) },
            )
        }
    }
}
