package com.mycards.wallet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycards.wallet.data.CardDraft
import com.mycards.wallet.ui.AppViewModel
import com.mycards.wallet.ui.screens.DetailScreen
import com.mycards.wallet.ui.screens.DocumentViewerScreen
import com.mycards.wallet.ui.screens.DocumentsScreen
import com.mycards.wallet.ui.screens.MenuScreen
import com.mycards.wallet.ui.screens.HomeScreen
import com.mycards.wallet.ui.screens.ReviewScreen
import com.mycards.wallet.ui.screens.ScannerScreen
import com.mycards.wallet.ui.screens.SettingsScreen
import com.mycards.wallet.ui.theme.LocalOrigin
import com.mycards.wallet.ui.theme.OriginTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val sharedImage = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { OriginTheme { AppRoot(sharedImage) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            sharedImage.value = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Scanner : Screen
    data class Review(val draft: CardDraft, val key: Long = System.nanoTime()) : Screen
    data class Detail(val id: String) : Screen
    data object Settings : Screen
    data object Menu : Screen
    data object Documents : Screen
    data class Document(val id: String) : Screen
}

@Composable
private fun AppRoot(sharedImage: MutableState<Uri?>) {
    val vm: AppViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stack = remember { mutableStateListOf<Screen>(Screen.Menu) }
    var forward by remember { mutableStateOf(true) }

    fun push(s: Screen) { forward = true; stack.add(s) }
    fun pop() { if (stack.size > 1) { forward = false; stack.removeAt(stack.lastIndex) } }
    fun replaceTop(s: Screen) { forward = true; stack[stack.lastIndex] = s }

    fun openImage(uri: Uri) {
        scope.launch {
            val draft = vm.draftFromImage(uri) ?: return@launch
            if (stack.last() is Screen.Scanner) replaceTop(Screen.Review(draft)) else push(Screen.Review(draft))
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) openImage(uri)
    }
    fun pickImage() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportTo(it) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importFrom(it) }
    }

    LaunchedEffect(sharedImage.value) {
        sharedImage.value?.let { uri ->
            sharedImage.value = null
            openImage(uri)
        }
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    Box(Modifier.fillMaxSize().background(LocalOrigin.current.background)) {
        AnimatedContent(
            targetState = stack.last(),
            transitionSpec = {
                val dur = 320
                if (targetState is Screen.Scanner) {
                    (fadeIn(tween(dur)) + scaleIn(tween(dur), initialScale = 0.94f)) togetherWith fadeOut(tween(dur / 2))
                } else if (forward) {
                    (slideInHorizontally(tween(dur)) { it / 3 } + fadeIn(tween(dur))) togetherWith
                        (slideOutHorizontally(tween(dur)) { -it / 8 } + fadeOut(tween(dur / 2)))
                } else {
                    (slideInHorizontally(tween(dur)) { -it / 8 } + fadeIn(tween(dur))) togetherWith
                        (slideOutHorizontally(tween(dur)) { it / 3 } + fadeOut(tween(dur / 2)))
                }
            },
            label = "nav",
        ) { screen ->
            when (screen) {
                Screen.Menu -> MenuScreen(
                    vm = vm,
                    onCards = { push(Screen.Home) },
                    onDocuments = { push(Screen.Documents) },
                )
                Screen.Documents -> DocumentsScreen(
                    vm = vm,
                    onBack = { pop() },
                    onOpen = { push(Screen.Document(it.id)) },
                )
                is Screen.Document -> DocumentViewerScreen(
                    vm = vm,
                    docId = screen.id,
                    onBack = { pop() },
                )
                Screen.Home -> HomeScreen(
                    vm = vm,
                    onBack = { pop() },
                    onOpen = { push(Screen.Detail(it.id)) },
                    onScan = { push(Screen.Scanner) },
                    onPickImage = { pickImage() },
                    onManual = { push(Screen.Review(CardDraft())) },
                    onExport = { exporter.launch("cards-backup.json") },
                    onImport = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onSettings = { push(Screen.Settings) },
                )
                Screen.Settings -> SettingsScreen(vm = vm, onBack = { if (stack.last() == Screen.Settings) pop() })
                Screen.Scanner -> ScannerScreen(
                    onResult = { scan ->
                        if (stack.last() is Screen.Scanner) replaceTop(Screen.Review(vm.draftFromScan(scan)))
                    },
                    onClose = { pop() },
                    onPickImage = { pickImage() },
                )
                is Screen.Review -> ReviewScreen(
                    vm = vm,
                    initial = screen.draft,
                    onBack = { pop() },
                    onSaved = { id ->
                        // после редактирования возвращаемся к уже открытой карте
                        if (stack.size >= 2 && stack[stack.lastIndex - 1] == Screen.Detail(id)) pop()
                        else replaceTop(Screen.Detail(id))
                    },
                )
                is Screen.Detail -> DetailScreen(
                    vm = vm,
                    cardId = screen.id,
                    onBack = { if (stack.last() == screen) pop() },
                    onEdit = { push(Screen.Review(vm.draftFromCard(it))) },
                )
            }
        }

        if (vm.busy) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(LocalOrigin.current.surface)
                        .padding(22.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(36.dp), color = LocalOrigin.current.accent)
                }
            }
        }
    }
}
