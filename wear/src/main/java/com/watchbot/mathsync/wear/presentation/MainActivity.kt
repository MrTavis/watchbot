package com.watchbot.mathsync.wear.presentation

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var crashLog by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent silent crash: show error on screen if any unexpected exception happens
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            throwable.printStackTrace()
            crashLog = "${throwable.javaClass.simpleName}: ${throwable.message}"
        }

        try {
            SolutionStorage.init(this)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (t: Throwable) {
            t.printStackTrace()
            crashLog = "Init error: ${t.message}"
        }

        setContent {
            GeminiWatchTheme {
                if (crashLog != null) {
                    // Safe crash display screen
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⚠ Ошибка:\n$crashLog",
                            color = Color(0xFFFF5252),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
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
}

@Composable
fun WatchApp(
    onToggleKeepScreen: (Boolean) -> Unit
) {
    val solutions by SolutionStorage.solutionsFlow.collectAsState()
    var currentIndex by remember { mutableIntStateOf(0) }
    var fontSizeSp by remember { mutableFloatStateOf(14.5f) }
    var isKeepScreenOn by remember { mutableStateOf(true) }

    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Ensure index stays in bounds
    val safeIndex = if (solutions.isNotEmpty()) {
        currentIndex.coerceIn(0, solutions.size - 1)
    } else {
        0
    }

    val currentSolution = solutions.getOrNull(safeIndex)

    // Request focus safely for rotary scroll after layout is ready
    LaunchedEffect(Unit) {
        delay(300)
        runCatching {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            // Physical / touch bezel rotary scroll
            .onRotaryScrollEvent { event ->
                coroutineScope.launch {
                    val delta = event.verticalScrollPixels
                    listState.scrollBy(delta)
                }
                true
            }
    ) {
        // Native Rock-solid Math & Markdown Viewer
        NativeMathViewer(
            markdownContent = currentSolution?.content ?: "",
            fontSizeSp = fontSizeSp,
            listState = listState,
            modifier = Modifier.fillMaxSize()
        )

        // --- TOP OVERLAY: Solution Navigator (if multiple solutions exist) ---
        if (solutions.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .background(Color(0xEE111111), shape = RoundedCornerShape(16.dp))
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
                        .padding(horizontal = 6.dp, vertical = 2.dp)
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
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // --- BOTTOM FLOATING CONTROLS (Font size & Screen On) ---
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .background(Color(0xEE080808), shape = RoundedCornerShape(20.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Font size down
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(0xFF1E1E1E), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { fontSizeSp = (fontSizeSp - 1.5f).coerceAtLeast(10f) },
                    modifier = Modifier.fillMaxSize(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E1E1E))
                ) {
                    Text("A-", fontSize = 10.sp, color = Color.White, textAlign = TextAlign.Center)
                }
            }

            // Screen On indicator / toggle
            Box(
                modifier = Modifier
                    .size(28.dp)
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
                    .size(28.dp)
                    .background(Color(0xFF1E1E1E), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { fontSizeSp = (fontSizeSp + 1.5f).coerceAtMost(24f) },
                    modifier = Modifier.fillMaxSize(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E1E1E))
                ) {
                    Text("A+", fontSize = 10.sp, color = Color.White, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
