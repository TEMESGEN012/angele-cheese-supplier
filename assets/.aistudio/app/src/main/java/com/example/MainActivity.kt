package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CustomerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.DairyViewModel

class MainActivity : ComponentActivity() {
    
    // Inject ViewModel with explicit application context mapping factory
    private val dairyViewModel: DairyViewModel by viewModels {
        DairyViewModel.provideFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                val screenState by dairyViewModel.currentScreen.collectAsStateWithLifecycle()
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (screenState) {
                        is AppScreen.Login -> {
                            AuthScreen(viewModel = dairyViewModel)
                        }
                        is AppScreen.Register -> {
                            AuthScreen(viewModel = dairyViewModel)
                        }
                        is AppScreen.CustomerHome -> {
                            CustomerScreen(viewModel = dairyViewModel)
                        }
                        is AppScreen.AdminHome -> {
                            AdminScreen(viewModel = dairyViewModel)
                        }
                    }
                }
            }
        }
    }
}
