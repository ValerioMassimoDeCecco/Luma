package it.luma.tapo.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import it.luma.tapo.data.LumaDesktopStorage
import it.luma.tapo.ui.LumaApp
import it.luma.tapo.ui.LumaDesktopViewModel
import it.luma.tapo.ui.LumaTheme
import it.luma.tapo.desktop.generated.resources.Res
import org.jetbrains.compose.resources.painterResource

@Composable
fun LumaWindow(onCloseRequest: () -> Unit) {
    val storage = remember { LumaDesktopStorage.createDefault() }
    val viewModel = remember { LumaDesktopViewModel(storage) }
    val state = viewModel.uiState.collectAsState(initial = viewModel.uiState.value)

    Window(
        onCloseRequest = onCloseRequest,
        title = "Luma per Tapo",
        state = rememberWindowState(width = Dp(1120), height = Dp(780)),
        icon = painterResource(Res.drawable.ic_launcher)
    ) {
        LumaTheme(darkTheme = state.value.isDark) {
            LumaApp(viewModel)
        }
    }
}

fun main() = application {
    LumaWindow(onCloseRequest = ::exitApplication)
}
