package com.printylan.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.printylan.R
import com.printylan.ui.theme.PrintylanTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var onFileNotPrintable: () -> Unit = {}

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !FilePrinter.print(this, uri)) onFileNotPrintable()
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PrintylanTheme {
                val windowSize = calculateWindowSizeClass(this)
                val printers by viewModel.printers.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val failedMessage = getString(R.string.print_file_failed)
                onFileNotPrintable = { scope.launch { snackbarHostState.showSnackbar(failedMessage) } }
                PrintersScreen(
                    printers = printers,
                    widthSizeClass = windowSize.widthSizeClass,
                    onOpenPrintSettings = ::openPrintSettings,
                    onAllowAccess = viewModel::allowAccess,
                    onDriverSelected = viewModel::setDriver,
                    onPrintFile = { pickFile.launch(FilePrinter.mimeTypes) },
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    /** Called when a printer is plugged in while the activity is on top. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.refresh()
    }

    private fun openPrintSettings() {
        try {
            startActivity(Intent(Settings.ACTION_PRINT_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
