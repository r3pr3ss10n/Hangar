package eu.r3pr3ss10n.hangar.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.r3pr3ss10n.hangar.ui.auth.LoginScreen
import eu.r3pr3ss10n.hangar.ui.auth.SetupNoticeScreen
import eu.r3pr3ss10n.hangar.ui.connect.ConnectScreen

/**
 * App is the root composable. It observes [AppState] and shows the matching
 * flow: connect → (web-only) setup notice / login → the authenticated shell.
 */
@Composable
fun App(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Crossfade(targetState = stateKey(state), label = "app-state") { key ->
        when (key) {
            StateKey.LOADING -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            StateKey.NEED_SERVER -> ConnectScreen(
                onConnected = viewModel::onServerConnected,
            )

            StateKey.NEED_SETUP -> SetupNoticeScreen(
                onRetry = viewModel::onServerConnected,
                onChangeServer = viewModel::onForgetServer,
            )

            StateKey.NEED_LOGIN -> LoginScreen(
                onLoggedIn = viewModel::onLoggedIn,
                onChangeServer = viewModel::onForgetServer,
            )

            StateKey.AUTHENTICATED -> {
                val user = (state as AppState.Authenticated).user
                MainShell(
                    user = user,
                    onLoggedOut = viewModel::onLoggedOut,
                    onForgetServer = viewModel::onForgetServer,
                )
            }
        }
    }
}

private enum class StateKey { LOADING, NEED_SERVER, NEED_SETUP, NEED_LOGIN, AUTHENTICATED }

private fun stateKey(state: AppState): StateKey = when (state) {
    AppState.Loading -> StateKey.LOADING
    AppState.NeedServer -> StateKey.NEED_SERVER
    AppState.NeedSetup -> StateKey.NEED_SETUP
    AppState.NeedLogin -> StateKey.NEED_LOGIN
    is AppState.Authenticated -> StateKey.AUTHENTICATED
}
