package com.watchbot.mathsync.wear.presentation

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.watchbot.mathsync.wear.SolutionStorage
import com.watchbot.mathsync.wear.presentation.theme.GeminiWatchTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SolutionStorage.init(this)

        // Keep screen on by default so formulas stay visible during study
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            GeminiWatchTheme {
                WatchApp(
                    onToggleKeepScreen = { enable ->
                        if (enable) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WatchApp(
    onToggleKeepScreen: (Boolean) -> Unit
) {
    val solutions by SolutionStorage.solutionsFlow.collectAsState()
    var currentIndex by remember { mutableIntStateOf(0) }
    var webViewHolder by remember { mutableStateOf<WatchWebViewHolder?>(null) }
    var isKeepScreenOn by remember { mutableStateOf(true) }

    // Ensure index stays in bounds when solutions change
    val safeIndex = if (solutions.isNotEmpty()) {
        currentIndex.coerceIn(0, solutions.size - 1)
    } else {
        0
    }

    val currentSolution = solutions.getOrNull(safeIndex)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            // Catch Rotary crown / touch bezel scroll on Galaxy Watch Ultra
            .onRotaryScrollEvent { event ->
                val delta = event.verticalScrollPixels.toInt()
                webViewHolder?.scrollByDelta(delta)
                true
            }
    ) {
        // Main Math / KaTeX Viewer
        if (currentSolution != null) {
            WatchMathView(
                markdownContent = currentSolution.content,
                onHolderReady = { holder ->
                    webViewHolder = holder
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Empty State
            WatchMathView(
                markdownContent = "",
                onHolderReady = { holder ->
                    webViewHolder = holder
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // --- TOP OVERLAY: Navigation & Status ---
        if (solutions.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .background(Color(0xCC111111), shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Prev button
                Text(
                    text = "◀",
                    fontSize = 12.sp,
                    color = if (safeIndex > 0) Color(0xFF00E5FF) else Color(0xFF444444),
                    modifier = Modifier
                        .clickable(enabled = safeIndex > 0) {
                            currentIndex = (safeIndex - 1).coerceAtLeast(0)
                        }
                        .padding(4.dp)
                )

                Text(
                    text = "${safeIndex + 1}/${solutions.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Next button
                Text(
                    text = "▶",
                    fontSize = 12.sp,
                    color = if (safeIndex < solutions.size - 1) Color(0xFF00E5FF) else Color(0xFF444444),
                    modifier = Modifier
                        .clickable(enabled = safeIndex < solutions.size - 1) {
                            currentIndex = (safeIndex + 1).coerceAtMost(solutions.size - 1)
                        }
                        .padding(4.dp)
                )
            }
        }

        // --- BOTTOM FLOATING CONTROLS (Font size & Screen On) ---
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .background(Color(0xCC000000), shape = RoundedCornerShape(20.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Font size down
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(Color(0xFF1E1E1E), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { webViewHolder?.changeFontSize(-2) },
                    modifier = Modifier.fillMaxSize(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E1E1E))
                ) {
                    Text("A-", fontSize = 10.sp, color = Color.White, textAlign = TextAlign.Center)
                }
            }

            // Screen On indicator / toggle
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(Color(0xFF1E1E1E), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        val newState = !isKeepScreenOn
                        isKeepScreenOn = newState
                        onToggleKeepScreen(newState)
                    },
                    modifier = Modifier.fillMaxSize(),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isKeepScreenOn) Color(0xFF004D40) else Color(0xFF1E1E1E)
                    )
                ) {
                    Text(
                        text = if (isKeepScreenOn) "💡" else "💤",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Font size up
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(Color(0xFF1E1E1E), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { webViewHolder?.changeFontSize(+2) },
                    modifier = Modifier.fillMaxSize(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E1E1E))
                ) {
                    Text("A+", fontSize = 10.sp, color = Color.White, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
