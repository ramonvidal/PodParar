package com.podparar.ui.root

import androidx.compose.runtime.Composable
import com.podparar.ui.navigation.PodPararNavHost

/** No accounts and no sign-in: the app opens straight onto its tabs. */
@Composable
fun RootScreen() {
    PodPararNavHost()
}
