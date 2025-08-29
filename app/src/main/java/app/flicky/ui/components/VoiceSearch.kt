package app.flicky.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.flicky.R

@Composable
fun VoiceSearchButton(onResult: (String) -> Unit) {
    val context = LocalContext.current
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

    IconButton(onClick = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, voiceSearchPrompt)
        }
        val pm = context.packageManager
        if (intent.resolveActivity(pm) != null) {
            launcher.launch(intent)
        } else {
            Toast.makeText(context, voiceSearchUnavailableMsg, Toast.LENGTH_SHORT).show()
        }
    }) {
        Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.voice_search))
    }
}