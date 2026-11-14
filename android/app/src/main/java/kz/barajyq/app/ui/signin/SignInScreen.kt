package kz.barajyq.app.ui.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kz.barajyq.app.R
import kz.barajyq.app.data.GoogleAuth
import kz.barajyq.app.data.GoogleSignInError
import kz.barajyq.app.data.SessionStore
import kz.barajyq.app.ui.common.IconBadge

/** Profile id of a user who continued without signing in. */
const val GUEST_ID = "guest"

@Composable
fun SignInScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val configured = remember { GoogleAuth.isConfigured() }
    val errCancelled = stringResource(R.string.signin_cancelled)
    val errGeneric = stringResource(R.string.signin_error)
    val errNoAccount = stringResource(R.string.signin_no_account)

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background),
            ),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            IconBadge(Icons.Filled.Explore, 96)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.signin_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Feature(Icons.Filled.AutoAwesome, stringResource(R.string.signin_feature_1))
                    Feature(Icons.Filled.Museum, stringResource(R.string.signin_feature_2))
                    Feature(Icons.Filled.Directions, stringResource(R.string.signin_feature_3))
                }
            }
            Spacer(Modifier.weight(1f).height(32.dp))
            // Guest mode: the app works fully without an account (Play "App access": no login needed).
            // The Google button is shown only when a client id is configured in the build.
            if (!configured) {
                Button(
                    onClick = { SessionStore.signIn(GUEST_ID, null, null); onSignedIn() },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(stringResource(R.string.signin_guest), style = MaterialTheme.typography.titleMedium) }
            } else Button(
                enabled = !loading,
                onClick = {
                    error = null
                    loading = true
                    scope.launch {
                        try {
                            val account = GoogleAuth.signIn(context)
                            SessionStore.signIn(account.googleId, account.email, account.displayName, account.idToken)
                            onSignedIn()
                        } catch (e: GoogleSignInError.Cancelled) {
                            error = errCancelled
                        } catch (e: GoogleSignInError.NoAccount) {
                            error = errNoAccount
                        } catch (e: Exception) {
                            error = errGeneric + (e.message?.let { "\n$it" } ?: "")
                        } finally {
                            loading = false
                        }
                    }
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("G", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.signin_google), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (configured) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { SessionStore.signIn(GUEST_ID, null, null); onSignedIn() },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text(stringResource(R.string.signin_guest)) }
            }
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(8.dp).size(22.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
