package com.example.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.android.presentation.navigation.AegisNavGraph
import com.example.android.ui.theme.AndroidTheme
import com.example.android.util.DeepLinkHandler
import com.example.android.util.DeepLinkRequest

class MainActivity : ComponentActivity() {

    private var incomingDeepLink by mutableStateOf<DeepLinkRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Immediately dismiss Android's system splash screen to show only the in-app built-in splash screen
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }

        incomingDeepLink = DeepLinkHandler.parseIntent(intent)

        enableEdgeToEdge()
        setContent {
            AndroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AegisNavGraph(
                        initialDeepLink = incomingDeepLink,
                        onDeepLinkConsumed = { incomingDeepLink = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingDeepLink = DeepLinkHandler.parseIntent(intent)
    }
}