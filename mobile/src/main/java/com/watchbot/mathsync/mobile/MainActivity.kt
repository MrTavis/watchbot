package com.watchbot.mathsync.mobile

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchbot.mathsync.mobile.ui.GeminiWatchSyncTheme
import com.watchbot.mathsync.mobile.ui.MathView
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var dataLayerManager: DataLayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dataLayerManager = DataLayerManager(this)

        setContent {
            GeminiWatchSyncTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    MainScreen(
                        dataLayerManager = dataLayerManager,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh connected watches when opening app
        kotlinx.coroutines.GlobalScope.launch {
            dataLayerManager.refreshConnectedNodes()
        }
    }
}

data class SolutionItem(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    dataLayerManager: DataLayerManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentContent by remember { mutableStateOf("") }
    var currentTitle by remember { mutableStateOf("Решение из Gemini") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Preview, 1: Editor, 2: History
    var autoSyncOnPaste by remember { mutableStateOf(true) }
    var syncStatusMessage by remember { mutableStateOf<String?>(null) }
    val historyList = remember { mutableStateListOf<SolutionItem>() }

    val connectedNodes by dataLayerManager.connectedNodes.collectAsState()

    LaunchedEffect(Unit) {
        dataLayerManager.refreshConnectedNodes()
    }

    fun doSendToWatch(title: String, content: String) {
        if (content.isBlank()) return
        coroutineScope.launch {
            val success = dataLayerManager.sendSolutionToWatch(title, content)
            if (success) {
                syncStatusMessage = "✓ Успешно отправлено на часы!"
                // Save to history if not duplicate of latest
                if (historyList.firstOrNull()?.content != content) {
                    historyList.add(0, SolutionItem(title = title, content = content))
                }
            } else {
                syncStatusMessage = "⚠ Ошибка: часы не ответили"
            }
            kotlinx.coroutines.delay(3000)
            syncStatusMessage = null
        }
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip() &&
            (clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true ||
             clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true)
        ) {
            val item = clipboard.primaryClip?.getItemAt(0)
            val text = item?.text?.toString() ?: ""
            if (text.isNotBlank()) {
                currentContent = text
                // Extract first header or default title
                val lines = text.lines()
                val firstHeading = lines.firstOrNull { it.startsWith("#") }
                    ?.trimStart('#', ' ') ?: "Решение ${historyList.size + 1}"
                currentTitle = firstHeading

                if (autoSyncOnPaste) {
                    doSendToWatch(currentTitle, text)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- TOP APP BAR & STATUS ---
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Gemini Watch Sync",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isConnected = connectedNodes.isNotEmpty()
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                    shape = RoundedCornerShape(4.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isConnected) {
                                "Подключено: ${connectedNodes.first().displayName}"
                            } else {
                                "Поиск часов в сети..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = { coroutineScope.launch { dataLayerManager.refreshConnectedNodes() } }) {
                    Text("🔄", fontSize = 18.sp)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // --- QUICK ACTION PANEL ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { pasteFromClipboard() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("📋 Вставить")
                    }

                    Button(
                        onClick = { doSendToWatch(currentTitle, currentContent) },
                        modifier = Modifier.weight(1f),
                        enabled = currentContent.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text("⌚ На часы")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Синхронизировать при вставке",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = autoSyncOnPaste,
                        onCheckedChange = { autoSyncOnPaste = it }
                    )
                }

                AnimatedVisibility(visible = syncStatusMessage != null) {
                    Text(
                        text = syncStatusMessage ?: "",
                        color = if (syncStatusMessage?.startsWith("✓") == true) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // --- TABS ---
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Формулы (Вид)") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Текст (Код)") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("История (${historyList.size})") }
            )
        }

        // --- CONTENT AREA ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                0 -> {
                    // Preview of formulas
                    if (currentContent.isBlank()) {
                        EmptyState(
                            onLoadSampleIntegral = {
                                currentContent = """
                                    ### Вычисление определенного интеграла
                                    Найдем значение интеграла Пуассона:
                                    $$I = \int_{-\infty}^{\infty} e^{-x^2} dx$$
                                    
                                    **Решение:**
                                    Рассмотрим квадрат интеграла в декартовых координатах:
                                    $$I^2 = \left(\int_{-\infty}^{\infty} e^{-x^2} dx\right) \left(\int_{-\infty}^{\infty} e^{-y^2} dy\right) = \int_{-\infty}^{\infty} \int_{-\infty}^{\infty} e^{-(x^2+y^2)} dx dy$$
                                    
                                    Перейдем к полярным координатам ($x = r\cos\theta, y = r\sin\theta, dx dy = r dr d\theta$):
                                    $$I^2 = \int_0^{2\pi} d\theta \int_0^{\infty} e^{-r^2} r dr = 2\pi \cdot \left[-\frac{1}{2} e^{-r^2}\right]_0^\infty = \pi$$
                                    
                                    Следовательно:
                                    $$I = \sqrt{\pi}$$
                                """.trimIndent()
                                currentTitle = "Интеграл Пуассона"
                            },
                            onLoadSamplePhysics = {
                                currentContent = """
                                    ### Физика: Уравнение Шрёдингера
                                    Стационарное одномерное уравнение:
                                    $$-\frac{\hbar^2}{2m} \frac{d^2\psi(x)}{dx^2} + U(x)\psi(x) = E\psi(x)$$
                                    
                                    где:
                                    * $\hbar = \frac{h}{2\pi}$ — редуцированная постоянная Планка;
                                    * $m$ — масса микрочастицы;
                                    * $\psi(x)$ — волновая функция;
                                    * $U(x)$ — потенциальная энергия;
                                    * $E$ — полная энергия частицы.
                                """.trimIndent()
                                currentTitle = "Уравнение Шрёдингера"
                            }
                        )
                    } else {
                        MathView(
                            markdownContent = currentContent,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                1 -> {
                    // Text editor / raw markdown
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        OutlinedTextField(
                            value = currentContent,
                            onValueChange = { currentContent = it },
                            label = { Text("Markdown + LaTeX текст от Gemini") },
                            placeholder = { Text("Вставьте сюда текст с $ формулами $ ...") },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { currentContent = "" }) {
                                Text("Очистить")
                            }
                        }
                    }
                }
                2 -> {
                    // History list
                    if (historyList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "История пуста.\nОтправленные решения появятся здесь.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(historyList) { item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentContent = item.content
                                            currentTitle = item.title
                                            selectedTab = 0
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = item.content.take(120).replace("\n", " ") + "...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    onLoadSampleIntegral: () -> Unit,
    onLoadSamplePhysics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📐",
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Здесь пока пусто",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Скопируйте решение с формулами в Gemini и нажмите кнопку «Вставить» сверху.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Или попробуйте демо-примеры:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onLoadSampleIntegral) {
                Text("Интеграл")
            }
            OutlinedButton(onClick = onLoadSamplePhysics) {
                Text("Физика")
            }
        }
    }
}
