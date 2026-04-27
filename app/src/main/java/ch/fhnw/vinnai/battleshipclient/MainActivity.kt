package ch.fhnw.vinnai.battleshipclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ch.fhnw.vinnai.battleshipclient.ui.theme.BattleshipCarrotTheme

// Use 10.0.2.2 to access the host's localhost from the Android emulator
const val DEFAULT_BASE_URL = "http://192.168.1.76:50003"

class MainActivity : ComponentActivity() {
    private val viewModel: BattleshipViewModel by viewModels()
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundManager = SoundManager(this)
        enableEdgeToEdge()
        setContent {
            BattleshipCarrotTheme {
                BattleshipApp(
                    viewModel = viewModel,
                    onTryFindCarrot = { soundManager.playDig() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        soundManager.resume()
    }

    override fun onStop() {
        super.onStop()
        soundManager.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundManager.release()
    }
}
