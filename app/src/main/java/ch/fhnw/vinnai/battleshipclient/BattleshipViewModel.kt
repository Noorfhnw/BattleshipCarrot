package ch.fhnw.vinnai.battleshipclient

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.fhnw.vinnai.battleshipclient.view.CellState
import ch.fhnw.vinnai.battleshipclient.view.PlacedShip
import ch.fhnw.vinnai.battleshipclient.view.ShipType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class BattleshipViewModel : ViewModel() {
    var pingResult by mutableStateOf<Boolean?>(null)
    var statusMessage by mutableStateOf("")
    var serverBaseUrl by mutableStateOf(DEFAULT_BASE_URL)
    var isMyTurn by mutableStateOf(false)
    var gameOver by mutableStateOf(false)
    var hasJoined by mutableStateOf(false)
    var isJoining by mutableStateOf(false)
    /** null while game is running, true if we won, false if we lost */
    var didIWin by mutableStateOf<Boolean?>(null)
    var sunkEnemyShips by mutableStateOf<List<String>>(emptyList())

    var myBoard = Array(10) { Array(10) { mutableStateOf(CellState.EMPTY) } }
    var opponentBoard = Array(10) { Array(10) { mutableStateOf(CellState.EMPTY) } }

    private var playerName = ""
    private var gameKey = ""

    val joinedGameId: String
        get() = gameKey

    // Ship placement state
    private val shipOrder = ShipType.entries          // Carrier → PatrolBoat
    var placedShips by mutableStateOf<List<PlacedShip>>(emptyList())
    var currentShipIndex by mutableIntStateOf(0)
    var isHorizontal by mutableStateOf(true)
    var placementBoard = Array(10) { Array(10) { mutableStateOf(CellState.EMPTY) } }
    var placementErrorMessage by mutableStateOf("")

    val currentShipType: ShipType?
        get() = shipOrder.getOrNull(currentShipIndex)

    val allShipsPlaced: Boolean
        get() = currentShipIndex >= shipOrder.size

    /** Toggle horizontal/vertical orientation. */
    fun toggleOrientation() {
        isHorizontal = !isHorizontal
    }

    /** Try to place the current ship at (x, y). Returns true on success. */
    fun placeShipAt(x: Int, y: Int) {
        val type = currentShipType ?: return
        val ship = PlacedShip(type, x, y, isHorizontal)

        // Bounds check
        val cells = ship.occupiedCells()
        if (cells.any { (cx, cy) -> cx !in 0..9 || cy !in 0..9 }) {
            placementErrorMessage = "${type.displayName} doesn't fit there!"
            return
        }

        // Overlap check
        val occupied = placedShips.flatMap { it.occupiedCells() }.toSet()
        if (cells.any { it in occupied }) {
            placementErrorMessage = "Overlaps with another ship!"
            return
        }

        // Place it
        placedShips = placedShips + ship
        cells.forEach { (cx, cy) -> placementBoard[cy][cx].value = CellState.SHIP }
        currentShipIndex++
        placementErrorMessage = ""
    }

    /** Undo the last placed ship. */
    fun undoLastShip() {
        if (placedShips.isEmpty()) return
        val last = placedShips.last()
        last.occupiedCells().forEach { (cx, cy) -> placementBoard[cy][cx].value = CellState.EMPTY }
        placedShips = placedShips.dropLast(1)
        currentShipIndex--
        placementErrorMessage = ""
    }

    /** Reset all placed ships. */
    fun resetPlacement() {
        placedShips = emptyList()
        currentShipIndex = 0
        placementErrorMessage = ""
        for (row in placementBoard) for (cell in row) cell.value = CellState.EMPTY
    }

    fun ping() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = apiUrl("/ping")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 100000
                    connection.readTimeout = 100000
                    connection.responseCode == 200
                } catch (_: Exception) {
                    false
                }
            }
            pingResult = result
        }
    }

    fun updateServerBaseUrl(rawValue: String): Boolean {
        val normalized = normalizeBaseUrl(rawValue) ?: run {
            statusMessage = "Invalid server URL"
            pingResult = false
            return false
        }

        serverBaseUrl = normalized
        pingResult = null
        if (statusMessage == "Invalid server URL") {
            statusMessage = ""
        }
        return true
    }

    fun joinGame(player: String, key: String) {
        if (isJoining) return
        playerName = player
        gameKey = key
        isJoining = true
        gameOver = false
        didIWin = null
        isMyTurn = false
        sunkEnemyShips = emptyList()
        statusMessage = "Waiting for opponent…"

        // Copy placed ships onto the defence board so they show during the game
        for (row in 0 until 10) for (col in 0 until 10) {
            myBoard[row][col].value = placementBoard[row][col].value
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = apiUrl("/game/join")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.setupJsonPost()

                    val body = JSONObject().apply {
                        put("player", player)
                        put("gamekey", key)
                        val ships = JSONArray().apply {
                            for (ship in placedShips) {
                                put(JSONObject().apply {
                                    put("ship", ship.type.name)
                                    put("x", ship.x)
                                    put("y", ship.y)
                                    put("orientation", if (ship.isHorizontal) "horizontal" else "vertical")
                                })
                            }
                        }
                        put("ships", ships)
                    }

                    connection.writeJson(body)

                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(response)
                        if (json.has("Error")) {
                            "Error: ${json.getString("Error")}"
                        } else {
                            handleGameStatus(json)
                            "Joined game"
                        }
                    } else {
                        val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        "Error: $error"
                    }
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            statusMessage = result
            if (result == "Joined game") {
                hasJoined = true
                if (!isMyTurn && !gameOver) {
                    waitForEnemyFire()
                }
            } else {
                isJoining = false
            }
        }
    }

    fun fire(x: Int, y: Int) {
        if (!isMyTurn || gameOver) return
        // Prevent firing on an already-shot cell
        if (opponentBoard[y][x].value != CellState.EMPTY) return

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = apiUrl("/game/fire")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.setupJsonPost()

                    val body = JSONObject().apply {
                        put("player", playerName)
                        put("gamekey", gameKey)
                        put("x", x)
                        put("y", y)
                    }

                    connection.writeJson(body)

                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(response)
                        if (json.has("Error")) {
                            "Error: ${json.getString("Error")}"
                        } else {
                            val hit = json.getBoolean("hit")
                            opponentBoard[y][x].value = if (hit) CellState.HIT else CellState.MISS

                            // Check if we won (all 5 enemy ships sunk)
                            val shipsSunk = json.optJSONArray("shipsSunk")
                            if (shipsSunk != null) {
                                sunkEnemyShips = shipsSunk.toShipNameList()
                            }
                            if (shipsSunk != null && shipsSunk.length() >= 5) {
                                gameOver = true
                                didIWin = true
                            } else {
                                isMyTurn = false
                                waitForEnemyFire()
                            }
                            "Shot fired"
                        }
                    } else {
                        "Error firing"
                    }
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            statusMessage = result
        }
    }

    private fun waitForEnemyFire() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = apiUrl("/game/enemyFire")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.setupJsonPost()

                    val body = JSONObject().apply {
                        put("player", playerName)
                        put("gamekey", gameKey)
                    }

                    connection.writeJson(body)

                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(response)
                        if (json.has("Error")) {
                            "Error: ${json.getString("Error")}"
                        } else {
                            handleGameStatus(json)
                            "Opponent moved"
                        }
                    } else {
                        "Error waiting for opponent"
                    }
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            statusMessage = result
        }
    }

    private fun handleGameStatus(json: JSONObject) {
        if (json.has("x") && !json.isNull("x")) {
            val x = json.getInt("x")
            val y = json.getInt("y")
            // Check if the enemy's shot hit one of our ships
            val wasShip = myBoard[y][x].value == CellState.SHIP
            myBoard[y][x].value = if (wasShip) CellState.HIT else CellState.MISS
        }
        gameOver = json.optBoolean("gameover", false)
        if (gameOver) {
            // If the game ended on the opponent's turn, we lost
            if (didIWin == null) didIWin = false
        } else {
            isMyTurn = true
        }
    }

    private fun apiUrl(path: String) = URL("$serverBaseUrl$path")

    private fun normalizeBaseUrl(rawValue: String): String? {
        val trimmed = rawValue.trim().trimEnd('/')
        if (trimmed.isBlank()) return null

        return try {
            val parsed = URL(trimmed)
            if (parsed.protocol !in listOf("http", "https") || parsed.host.isBlank()) {
                null
            } else {
                trimmed
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun HttpURLConnection.setupJsonPost() {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 100000
        readTimeout = 600000
        setRequestProperty("Content-Type", "application/json")
    }

    private fun HttpURLConnection.writeJson(body: JSONObject) {
        outputStream.bufferedWriter().use { it.write(body.toString()) }
    }

    private fun JSONArray.toShipNameList(): List<String> {
        val names = mutableListOf<String>()
        for (i in 0 until length()) {
            val rawName = optString(i).trim()
            if (rawName.isNotEmpty()) {
                names += rawName.toShipDisplayName()
            }
        }
        return names.distinct()
    }

    private fun String.toShipDisplayName(): String {
        val normalized = filter { it.isLetterOrDigit() }.lowercase()
        return ShipType.entries
            .firstOrNull { it.name.filter { ch -> ch.isLetterOrDigit() }.lowercase() == normalized }
            ?.displayName
            ?: this
    }
}
