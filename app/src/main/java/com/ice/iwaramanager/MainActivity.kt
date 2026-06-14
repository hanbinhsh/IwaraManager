package com.ice.iwaramanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ice.iwaramanager.ui.theme.IwaraManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            IwaraManagerTheme {
                AppRoot()
            }
        }
    }
}