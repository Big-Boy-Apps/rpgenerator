package org.bigboyapps.rngenerator

import androidx.compose.ui.window.ComposeUIViewController
import com.rpgenerator.core.persistence.DriverFactory

fun MainViewController() = ComposeUIViewController {
    App(driverFactory = { DriverFactory().createDriver() })
}
