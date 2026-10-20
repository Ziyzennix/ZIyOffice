package org.ziyoffice

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.ziyoffice.design.ZiyOfficeTheme
import org.ziyoffice.ui.ZiyOfficeApp

class MainActivity : ComponentActivity() {

    /**
     * A document handed to us by another app ("Open with", "Share to").
     * Kept as state so the Compose shell picks it up whether it arrives with
     * the launch intent or with a later [onNewIntent] while we are running.
     */
    private var incoming by mutableStateOf<IncomingFile?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incoming = incomingFrom(intent)
        setContent {
            ZiyOfficeTheme {
                ZiyOfficeApp(
                    incomingUri = incoming?.uri,
                    incomingMimeType = incoming?.mimeType,
                    onIncomingConsumed = { incoming = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingFrom(intent)?.let { incoming = it }
    }

    /**
     * The document a VIEW or SEND intent points at, with the MIME type the
     * sending app declared. The type matters: content providers sometimes report
     * a display name with no extension, and without a type such a file would be
     * unopenable.
     */
    private fun incomingFrom(intent: Intent?): IncomingFile? {
        val action = intent?.action ?: return null
        val uri = when (action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        } ?: return null
        return IncomingFile(uri, intent.type)
    }

    private data class IncomingFile(val uri: Uri, val mimeType: String?)
}
