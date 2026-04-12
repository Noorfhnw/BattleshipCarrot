package ch.fhnw.vinnai.battleshipclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ch.fhnw.vinnai.battleshipclient.ui.theme.BattleshipCarrotTheme

// Use 10.0.2.2 to access the host's localhost from the Android emulator
const val BASE_URL = "http://10.0.2.2:50003"

class MainActivity : ComponentActivity() {
    private val viewModel: BattleshipViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BattleshipCarrotTheme {
                BattleshipApp(viewModel)
            }
        }
    }
}
