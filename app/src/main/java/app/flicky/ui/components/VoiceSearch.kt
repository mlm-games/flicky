package app.flicky.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.flicky.R
import app.flicky.ui.components.snackbar.SnackbarManager
import org.koin.compose.koinInject

@Composable
fun VoiceSearchButton(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val snackbarManager: SnackbarManager = koinInject()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val data = res.data
            val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = matches?.firstOrNull().orEmpty()
            if (text.isNotBlank()) onResult(text)
        }
    }

    val voiceSearchPrompt = stringResource(id = R.string.voice_search_prompt)
    val voiceSearchUnavailableMsg = stringResource(id = R.string.voice_search_unavailable)

    IconButton(
        onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, voiceSearchPrompt)
            }
            val pm = context.packageManager
            if (intent.resolveActivity(pm) != null) {
                launcher.launch(intent)
            } else {
                snackbarManager.show(voiceSearchUnavailableMsg)
            }
        },
        modifier = modifier // sibling of search bar on TV → DPAD can focus it
    ) {
        Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.voice_search))
    }
}
