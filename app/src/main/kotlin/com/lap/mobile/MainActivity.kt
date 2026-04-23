package com.lap.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lap.mobile.ui.theme.LapTheme

private const val ROUTE_HOME = "home"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}

@Composable
private fun AppRoot() {
    val navController = rememberNavController()
    LapTheme {
        Surface {
            NavHost(navController = navController, startDestination = ROUTE_HOME) {
                composable(ROUTE_HOME) {
                    Text(text = stringResource(R.string.app_name))
                }
            }
        }
    }
}

@Preview
@Composable
private fun AppRootPreview() {
    LapTheme {
        Surface { Text(text = "LAP") }
    }
}
