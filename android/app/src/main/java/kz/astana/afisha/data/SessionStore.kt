package kz.astana.afisha.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kz.astana.afisha.domain.AppLanguage
import kz.astana.afisha.domain.City
import kz.astana.afisha.domain.Interest
import kz.astana.afisha.domain.KazakhstanCities
import kz.astana.afisha.domain.UserProfile

/**
 * Single DataStore file `files/datastore/session.preferences_pb`.
 * It lives in app-private storage, so it survives `installDebug` / store updates
 * (only uninstall or "Clear data" wipes it). It is also included in Auto Backup
 * (see res/xml/backup_rules.xml + data_extraction_rules.xml) so it can be restored
 * after a reinstall on a device with Google backup enabled.
 * Never cleared on version bumps.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

object SessionStore {
    private object K {
        val googleId = stringPreferencesKey("google_id")
        val email = stringPreferencesKey("email")
        val name = stringPreferencesKey("display_name")
        val token = stringPreferencesKey("id_token")
        val interests = stringSetPreferencesKey("interests")
        val cityId = stringPreferencesKey("city_id")
        val cityName = stringPreferencesKey("city_name")
        val hidden = stringSetPreferencesKey("hidden_event_ids")
        val language = stringPreferencesKey("language")
        val saved = stringSetPreferencesKey("saved_event_ids")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: DataStore<Preferences>

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()
    private val _city = MutableStateFlow<City?>(null)
    val city: StateFlow<City?> = _city.asStateFlow()
    private val _hidden = MutableStateFlow<Set<String>>(emptySet())
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()
    private val _language = MutableStateFlow<AppLanguage?>(null)
    val language: StateFlow<AppLanguage?> = _language.asStateFlow()
    private val _saved = MutableStateFlow<Set<String>>(emptySet())
    /** Events and places the user bookmarked ("Сохранённые" tab). */
    val saved: StateFlow<Set<String>> = _saved.asStateFlow()

    /** Interests are kept even after sign-out, so re-signing in restores them. */
    private var savedInterests: Set<Interest> = emptySet()

    fun init(context: Context) {
        if (::store.isInitialized) return
        store = context.applicationContext.dataStore
        scope.launch {
            val p = runCatching { store.data.first() }.getOrNull()
            if (p != null) {
                savedInterests = p[K.interests].orEmpty()
                    .mapNotNull { n -> runCatching { Interest.valueOf(n) }.getOrNull() }.toSet()
                _profile.value = p[K.googleId]?.let {
                    UserProfile(
                        googleId = it,
                        idToken = p[K.token],
                        email = p[K.email],
                        displayName = p[K.name],
                        interests = savedInterests,
                    )
                }
                _city.value = p[K.cityId]?.let { id -> KazakhstanCities.byId(id) ?: City(id, p[K.cityName] ?: id) }
                _hidden.value = p[K.hidden].orEmpty()
                _saved.value = p[K.saved].orEmpty()
                _language.value = AppLanguage.fromTag(p[K.language])
            }
            _ready.value = true
        }
    }

    private fun persist(block: suspend (MutablePreferences) -> Unit) {
        scope.launch { store.edit { block(it) } }
    }

    fun signIn(googleId: String, email: String?, displayName: String?, idToken: String? = null) {
        _profile.value = UserProfile(googleId, idToken, email, displayName, interests = savedInterests)
        persist {
            it[K.googleId] = googleId
            if (email != null) it[K.email] = email else it.remove(K.email)
            if (displayName != null) it[K.name] = displayName else it.remove(K.name)
            if (idToken != null) it[K.token] = idToken else it.remove(K.token)
        }
    }

    fun saveOnboarding(interests: Set<Interest>, exclusions: Set<Interest> = emptySet()) = setInterests(interests)

    fun setInterests(interests: Set<Interest>) {
        savedInterests = interests
        _profile.value = _profile.value?.copy(interests = interests)
        persist { it[K.interests] = interests.map { i -> i.name }.toSet() }
    }

    fun setCity(city: City) {
        _city.value = city
        persist { it[K.cityId] = city.id; it[K.cityName] = city.name }
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
        persist { it[K.language] = lang.tag }
    }

    fun hideEvent(id: String) {
        val next = _hidden.value + id
        _hidden.value = next
        persist { it[K.hidden] = next }
    }

    fun unhideEvent(id: String) {
        val next = _hidden.value - id
        _hidden.value = next
        persist { it[K.hidden] = next }
    }

    fun toggleSaved(id: String) {
        val next = if (id in _saved.value) _saved.value - id else _saved.value + id
        _saved.value = next
        persist { it[K.saved] = next }
    }

    fun clearHidden() {
        _hidden.value = emptySet()
        persist { it.remove(K.hidden) }
    }

    /** Signs out of Google but keeps city, language, interests and hidden items. */
    fun signOut() {
        _profile.value = null
        persist {
            it.remove(K.googleId); it.remove(K.email); it.remove(K.name); it.remove(K.token)
        }
    }
}
