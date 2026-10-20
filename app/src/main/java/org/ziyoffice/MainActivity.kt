package org.ziyoffice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.ziyoffice.design.ZiyOfficeTheme
import org.ziyoffice.ui.ZiyOfficeApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZiyOfficeTheme {
                ZiyOfficeApp()
            }
        }
    }
}
