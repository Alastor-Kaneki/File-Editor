package com.alastorkaneki.fileeditor

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.alastorkaneki.fileeditor.ui.StudioApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: FileEditorViewModel by viewModels()

    private var hasAudioPermission by mutableStateOf(false)
    private var hasImagePermission by mutableStateOf(false)
    private var hasVideoPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshPermissionState()
    }

    private val writeLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onWritePermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is UiEffect.RequestWriteAccess -> {
                            val request = IntentSenderRequest.Builder(
                                effect.pendingIntent.intentSender,
                            ).build()
                            writeLauncher.launch(request)
                        }
                    }
                }
            }
        }

        refreshPermissionState()

        setContent {
            StudioApp(
                viewModel = viewModel,
                hasAudioPermission = hasAudioPermission,
                hasImagePermission = hasImagePermission,
                hasVideoPermission = hasVideoPermission,
                onRequestAllPermissions = ::requestMediaPermissions,
            )
        }
    }

    private fun requestMediaPermissions() {
        permissionLauncher.launch(requiredPermissions())
    }

    private fun refreshPermissionState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasAudioPermission = hasPermission(Manifest.permission.READ_MEDIA_AUDIO)
            hasImagePermission = hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
            hasVideoPermission = hasPermission(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            val sharedRead = hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            hasAudioPermission = sharedRead
            hasImagePermission = sharedRead
            hasVideoPermission = sharedRead
        }
        viewModel.onPermissionResult(hasAudioPermission)
    }

    private fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
