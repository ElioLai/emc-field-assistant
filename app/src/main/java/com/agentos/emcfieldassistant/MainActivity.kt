package com.agentos.emcfieldassistant

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONArray
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.Executor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        EmcLog.init(applicationContext)
        EmcLog.i("MainActivity.onCreate")
        super.onCreate(savedInstanceState)
        setContent {
            EmcFieldAssistantApp()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && VolumeShutter.trigger()) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

private object EmcLog {
    private const val Tag = "EMCFieldAssistant"
    private const val MaxLogBytes = 512 * 1024
    private var context: Context? = null
    private var installed = false

    fun init(appContext: Context) {
        context = appContext.applicationContext
        if (installed) return
        installed = true
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            e("Uncaught exception on ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
        i("Log initialized")
    }

    fun i(message: String) {
        Log.i(Tag, message)
        write("INFO", message, null)
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(Tag, message, throwable)
        write("WARN", message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(Tag, message, throwable)
        write("ERROR", message, throwable)
    }

    private fun write(level: String, message: String, throwable: Throwable?) {
        val appContext = context ?: return
        runCatching {
            val logDir = appContext.filesDir.resolve("logs").apply { mkdirs() }
            val logFile = logDir.resolve("emc_field_assistant.log")
            if (logFile.exists() && logFile.length() > MaxLogBytes) {
                logDir.resolve("emc_field_assistant.previous.log").delete()
                logFile.renameTo(logDir.resolve("emc_field_assistant.previous.log"))
            }
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).format(Date())
            val stackTrace = throwable?.let {
                val writer = StringWriter()
                it.printStackTrace(PrintWriter(writer))
                "\n${writer}"
            } ?: ""
            logFile.appendText("$timestamp $level $message$stackTrace\n")
        }
    }
}

private object VolumeShutter {
    var action: (() -> Unit)? = null

    fun trigger(): Boolean {
        val current = action ?: return false
        current()
        return true
    }
}

private val EmcPrimary = Color(0xFF5F756D)
private val EmcPrimaryDark = Color(0xFF435C53)

private val TestGroups = listOf(
    TestGroup(
        "发射测试",
        listOf(
            TestItem("1", "CE", "传导发射", listOf("模拟手是否需要并已连接", "10 cm 绝缘垫板是否放置", "AMN/LISN、接地和线缆走向是否拍清楚", "EUT 工作模式和供电配置是否确认")),
            TestItem("2", "RE", "辐射发射", listOf("测试距离是否确认", "天线极化和高度扫描状态是否确认", "转台/EUT 位置是否拍清楚", "线缆垂落和辅助设备位置是否一致")),
            TestItem("3", "Harmonics", "谐波电流", listOf("供电电压、频率和功率状态是否确认", "EUT 工作模式是否为最不利或规定模式", "电源分析仪连接和负载条件是否拍清楚")),
            TestItem("4", "Flicker", "电压波动和闪烁", listOf("供电条件和观察周期是否确认", "EUT 运行循环是否符合测试要求", "电源分析仪连接和负载变化状态是否拍清楚"))
        )
    ),
    TestGroup(
        "抗扰度测试",
        listOf(
            TestItem("5", "ESD", "静电放电", listOf("放电点、HCP/VCP 和接地线是否明确", "直接/间接放电位置是否拍清楚", "样品工作状态和监控方式是否记录")),
            TestItem("6", "RS", "辐射抗扰", listOf("场强校准状态是否确认", "EUT 朝向、距离和监控方式是否明确", "性能判据观察点是否拍清楚")),
            TestItem("7", "EFT", "电快速瞬变", listOf("耦合夹或 CDN 连接方式是否拍清楚", "端口、线缆长度和接地状态是否确认", "等级、极性和工作模式是否一致")),
            TestItem("8", "Surge", "浪涌", listOf("耦合/去耦网络连接是否正确", "端口、线缆和保护接地是否拍清楚", "等级、相位和极性配置是否确认")),
            TestItem("9", "CS", "传导抗扰", listOf("注入夹/CDN/EM 钳配置是否确认", "校准路径和实际测试路径是否区分", "监控方式和线缆布置是否拍清楚")),
            TestItem("10", "Dips", "电压跌落", listOf("供电电压和频率是否确认", "跌落等级、持续时间和相位是否确认", "样品恢复状态是否记录")),
            TestItem("11", "PFMF", "工频磁场", listOf("线圈位置和 EUT 方位是否确认", "场强等级和持续时间是否确认", "监控方式是否拍清楚"))
        )
    ),
    TestGroup(
        "其他测试",
        listOf(
            TestItem("12", "高频手术干扰", "高频手术设备干扰", listOf("高频手术设备输出模式和附件连接是否确认", "干扰耦合位置、线缆走向和接地状态是否拍清楚", "样品监控方式和性能判据是否记录")),
            TestItem("13", "邻近磁场", "邻近磁场", listOf("线圈/磁场源位置和距离是否确认", "EUT 方位、端口和敏感区域是否拍清楚", "场强等级、频率点和监控方式是否记录"))
        )
    ),
    TestGroup(
        "样品资料",
        listOf(
            TestItem("14", "样品铭牌外观", "样品信息", listOf("铭牌、型号、序列号是否清晰", "主检与覆盖型号外观差异是否拍清楚", "附件、包装标签和关键端口是否补拍"))
        )
    )
)

private val TestItems = TestGroups.flatMap { it.items }

data class TestGroup(
    val title: String,
    val items: List<TestItem>
)

data class TestItem(
    val number: String,
    val code: String,
    val title: String,
    val reminders: List<String>
) {
    val folderName: String = "$number $code"
}

data class ConfigOption(
    val label: String = "默认",
    val probe: String = "",
    val power: String = "",
    val runMode: String = ""
) {
    val displayLabel: String = label.ifBlank { "默认" }
}

data class ModelConfig(
    val role: String,
    val name: String,
    val configs: List<ConfigOption> = listOf(ConfigOption())
) {
    val folderName: String = "${role}_${name}".sanitizePathPart()
}

data class ProjectConfig(
    val workOrder: String,
    val manufacturer: String,
    val projectName: String,
    val models: List<ModelConfig>
) {
    val folderName: String
        get() = listOf(workOrder, manufacturer, primaryModelName(), projectName)
            .filter { it.isNotBlank() }
            .joinToString("_")
            .sanitizePathPart()

    fun hasMainAndCoverage(): Boolean {
        val roles = models.map { it.role.trim() }.toSet()
        return "主检" in roles && "覆盖" in roles
    }

    fun hasMultipleRoleModelCombos(): Boolean {
        return models.map { it.role.trim() to it.name.trim() }.toSet().size > 1
    }

    private fun primaryModelName(): String {
        return models.firstOrNull { it.role.trim() == "主检" }?.name
            ?: models.firstOrNull()?.name
            ?: ""
    }
}

data class PhotoSpec(
    val fileName: String,
    val relativePath: String
)

private sealed interface Screen {
    data object Home : Screen
    data class Capture(val item: TestItem) : Screen
}

private enum class MainTab {
    Config,
    Test
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmcFieldAssistantApp() {
    val density = LocalDensity.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var project by remember {
        mutableStateOf(
            ProjectConfig(
                workOrder = "",
                manufacturer = "",
                projectName = "",
                models = listOf(ModelConfig("主检", "A"))
            )
        )
    }
    var selectedModel by remember { mutableStateOf(project.models.first()) }
    var selectedConfig by remember { mutableStateOf(project.models.first().configs.first()) }
    var retakeMode by remember { mutableStateOf(false) }
    var mainTab by remember { mutableStateOf(MainTab.Config) }
    var photoCountRefreshKey by remember { mutableStateOf(0) }

    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                primary = EmcPrimaryDark,
                surface = Color(0xFFF7F8F8),
                background = Color(0xFFF7F8F8)
            )
        ) {
            Surface(color = Color(0xFFF7F8F8), modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    topBar = {
                        if (screen == Screen.Home && mainTab == MainTab.Config) {
                            TopAppBar(
                                title = {
                                    Column {
                                        Text("EMC现场助手", fontWeight = FontWeight.Bold)
                                        Text(
                                            text = project.workOrder.ifBlank { "未绑定工单" },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFF617066)
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF7F8F8))
                            )
                        }
                    },
                    bottomBar = {
                        if (screen == Screen.Home) {
                            NavigationBar(containerColor = Color.White) {
                                NavigationBarItem(
                                    selected = mainTab == MainTab.Config,
                                    onClick = { mainTab = MainTab.Config },
                                    icon = { ConfigTabIcon(mainTab == MainTab.Config) },
                                    label = { Text("配置") }
                                )
                                NavigationBarItem(
                                    selected = mainTab == MainTab.Test,
                                    onClick = { mainTab = MainTab.Test },
                                    icon = { CameraTabIcon(mainTab == MainTab.Test) },
                                    label = { Text("测试") }
                                )
                            }
                        }
                    }
                ) { padding ->
                    when (val current = screen) {
                        Screen.Home -> HomeScreen(
                            padding = padding,
                            project = project,
                            selectedModel = selectedModel,
                            selectedConfig = selectedConfig,
                            retakeMode = retakeMode,
                            activeTab = mainTab,
                            photoCountRefreshKey = photoCountRefreshKey,
                            onProjectChange = {
                                project = it
                                selectedModel = it.models.first()
                                selectedConfig = it.models.first().configs.firstOrNull() ?: ConfigOption()
                            },
                            onModelChange = {
                                selectedModel = it
                                selectedConfig = it.configs.firstOrNull() ?: ConfigOption()
                            },
                            onConfigChange = { selectedConfig = it },
                            onRetakeModeChange = { retakeMode = it },
                            onTabChange = { mainTab = it },
                            onOpenCapture = { screen = Screen.Capture(it) }
                        )

                        is Screen.Capture -> CaptureScreen(
                            padding = padding,
                            project = project,
                            model = selectedModel,
                            config = selectedConfig,
                            item = current.item,
                            onBack = {
                                photoCountRefreshKey++
                                screen = Screen.Home
                            }
                        )

                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigTabIcon(selected: Boolean) {
    val tint = if (selected) EmcPrimaryDark else Color(0xFF6F7671)
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(5.dp.toPx(), 4.5.dp.toPx()),
            size = Size(14.dp.toPx(), 16.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
            style = stroke
        )
        drawLine(tint, Offset(9.dp.toPx(), 8.dp.toPx()), Offset(15.dp.toPx(), 8.dp.toPx()), strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(tint, Offset(9.dp.toPx(), 12.dp.toPx()), Offset(15.dp.toPx(), 12.dp.toPx()), strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(tint, Offset(9.dp.toPx(), 16.dp.toPx()), Offset(13.dp.toPx(), 16.dp.toPx()), strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(tint, radius = 2.1.dp.toPx(), center = Offset(12.dp.toPx(), 4.5.dp.toPx()))
    }
}

@Composable
private fun CameraTabIcon(selected: Boolean) {
    val tint = if (selected) EmcPrimaryDark else Color(0xFF6F7671)
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(4.dp.toPx(), 7.dp.toPx()),
            size = Size(16.dp.toPx(), 12.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = stroke
        )
        drawLine(tint, Offset(8.dp.toPx(), 7.dp.toPx()), Offset(10.dp.toPx(), 4.5.dp.toPx()), strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(tint, Offset(10.dp.toPx(), 4.5.dp.toPx()), Offset(14.dp.toPx(), 4.5.dp.toPx()), strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(tint, Offset(14.dp.toPx(), 4.5.dp.toPx()), Offset(16.dp.toPx(), 7.dp.toPx()), strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(tint, radius = 3.4.dp.toPx(), center = Offset(12.dp.toPx(), 13.dp.toPx()), style = stroke)
        drawCircle(tint, radius = 1.1.dp.toPx(), center = Offset(17.dp.toPx(), 10.dp.toPx()))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(
    padding: PaddingValues,
    project: ProjectConfig,
    selectedModel: ModelConfig,
    selectedConfig: ConfigOption,
    retakeMode: Boolean,
    activeTab: MainTab,
    photoCountRefreshKey: Int,
    onProjectChange: (ProjectConfig) -> Unit,
    onModelChange: (ModelConfig) -> Unit,
    onConfigChange: (ConfigOption) -> Unit,
    onRetakeModeChange: (Boolean) -> Unit,
    onTabChange: (MainTab) -> Unit,
    onOpenCapture: (TestItem) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("emc_field_assistant", Context.MODE_PRIVATE)
    }
    var workOrder by remember(project.workOrder) { mutableStateOf(project.workOrder) }
    var manufacturer by remember(project.manufacturer) { mutableStateOf(project.manufacturer) }
    var projectName by remember(project.projectName) { mutableStateOf(project.projectName) }
    var pendingItem by remember { mutableStateOf<TestItem?>(null) }
    var photoCounts by remember(project.folderName, selectedModel.folderName) {
        mutableStateOf(emptyMap<String, Int>())
    }

    LaunchedEffect(project.folderName, selectedModel.folderName, activeTab, photoCountRefreshKey) {
        photoCounts = loadPhotoCounts(context, project, selectedModel)
    }

    val projectJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure {
                EmcLog.w("Persist project JSON permission failed", it)
            }
            parseProjectJson(context, uri)?.let { parsedProject ->
                onProjectChange(parsedProject)
                Toast.makeText(context, "已读取 project.json", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(context, "Project JSON 未识别，请检查文件结构", Toast.LENGTH_LONG).show()
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }.onFailure {
                EmcLog.w("Persist folder permission failed", it)
            }
            prefs.edit().putString("last_project_tree_uri", uri.toString()).apply()
            parseProjectFolder(context, uri)?.let { parsedProject ->
                onProjectChange(parsedProject)
                Toast.makeText(context, "已读取已有项目文件夹", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(context, "文件夹未识别，请选择工单项目目录", Toast.LENGTH_LONG).show()
        }
    }

    when (activeTab) {
        MainTab.Config -> ConfigPage(
            padding = padding,
            project = project,
            selectedModel = selectedModel,
            selectedConfig = selectedConfig,
            retakeMode = retakeMode,
            workOrder = workOrder,
            manufacturer = manufacturer,
            projectName = projectName,
            onImportProjectJson = { projectJsonLauncher.launch(arrayOf("application/json", "text/json", "text/*", "*/*")) },
            onImportFolder = {
                val lastFolder = prefs.getString("last_project_tree_uri", null)?.let(Uri::parse)
                folderLauncher.launch(lastFolder)
            },
            onModelChange = onModelChange,
            onConfigChange = onConfigChange,
            onRetakeModeChange = onRetakeModeChange,
            onOpenTestTab = { onTabChange(MainTab.Test) }
        )

        MainTab.Test -> TestPage(
            padding = padding,
            project = project,
            selectedModel = selectedModel,
            selectedConfig = selectedConfig,
            retakeMode = retakeMode,
            photoCounts = photoCounts,
            onOpenCapture = { item ->
                if (retakeMode) onOpenCapture(item) else pendingItem = item
            }
        )
    }

    pendingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingItem = null },
            title = { Text("${item.number} ${item.code} 注意事项") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.reminders.forEach { Text("• $it") }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingItem = null
                        onOpenCapture(item)
                    }
                ) {
                    Text("进入拍照")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingItem = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigPage(
    padding: PaddingValues,
    project: ProjectConfig,
    selectedModel: ModelConfig,
    selectedConfig: ConfigOption,
    retakeMode: Boolean,
    workOrder: String,
    manufacturer: String,
    projectName: String,
    onImportProjectJson: () -> Unit,
    onImportFolder: () -> Unit,
    onModelChange: (ModelConfig) -> Unit,
    onConfigChange: (ConfigOption) -> Unit,
    onRetakeModeChange: (Boolean) -> Unit,
    onOpenTestTab: () -> Unit
) {
    val configScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(configScrollState)
                    .padding(end = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "本次测试",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = retakeMode,
                        onClick = { onRetakeModeChange(!retakeMode) },
                        label = { Text("补拍模式") }
                    )
                }

                ProjectSummaryLine(
                    workOrder = workOrder,
                    manufacturer = manufacturer,
                    projectName = projectName
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onImportProjectJson,
                        colors = ButtonDefaults.buttonColors(containerColor = EmcPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("导入 JSON")
                    }
                    OutlinedButton(
                        onClick = onImportFolder,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("选文件夹")
                    }
                }

                SelectionSection(
                    title = "型号",
                    summary = "${selectedModel.role} ${selectedModel.name}",
                    showSummary = project.models.size <= 1
                ) {
                    if (project.models.size > 1) {
                        project.models.forEach { model ->
                            FilterChip(
                                selected = model == selectedModel,
                                onClick = { onModelChange(model) },
                                label = { Text("${model.role} ${model.name}") }
                            )
                        }
                    }
                }

                val configs = selectedModel.configs.distinct()
                SelectionSection(
                    title = "配置",
                    summary = selectedConfig.displayLabel,
                    showSummary = configs.size <= 1
                ) {
                    if (configs.size > 1) {
                        configs.forEach { config ->
                            FilterChip(
                                selected = config == selectedConfig,
                                onClick = { onConfigChange(config) },
                                label = { Text(config.displayLabel) }
                            )
                        }
                    }
                }
            }
            ConfigScrollIndicator(configScrollState)
        }

        Button(
            onClick = onOpenTestTab,
            colors = ButtonDefaults.buttonColors(containerColor = EmcPrimary),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text("进入测试", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProjectSummaryLine(
    workOrder: String,
    manufacturer: String,
    projectName: String
) {
    Text(
        listOf(
            workOrder.ifBlank { "未导入工单" },
            manufacturer.ifBlank { "未导入厂家" },
            projectName.ifBlank { "未导入样品" }
        ).joinToString("｜"),
        color = Color(0xFF617066),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SelectionSection(
    title: String,
    summary: String,
    showSummary: Boolean,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (showSummary) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE5E5E5), RoundedCornerShape(8.dp))
            ) {
                Text(
                    summary.ifBlank { "默认" },
                    color = Color(0xFF202020),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun BoxScope.ConfigScrollIndicator(scrollState: ScrollState) {
    if (scrollState.maxValue <= 0) return

    Canvas(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(3.dp)
    ) {
        val radius = 1.5.dp.toPx()
        val trackWidth = 3.dp.toPx()
        val thumbHeight = (size.height * 0.32f).coerceAtLeast(28.dp.toPx())
        val available = (size.height - thumbHeight).coerceAtLeast(0f)
        val progress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
        val top = available * progress

        drawRoundRect(
            color = Color(0x1F435C53),
            topLeft = Offset(0f, 0f),
            size = Size(trackWidth, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )
        drawRoundRect(
            color = Color(0x88435C53),
            topLeft = Offset(0f, top),
            size = Size(trackWidth, thumbHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )
    }
}

@Composable
private fun TestPage(
    padding: PaddingValues,
    project: ProjectConfig,
    selectedModel: ModelConfig,
    selectedConfig: ConfigOption,
    retakeMode: Boolean,
    photoCounts: Map<String, Int>,
    onOpenCapture: (TestItem) -> Unit
) {
    val itemsWithPhotos = TestItems.count { (photoCounts[it.folderName] ?: 0) > 0 }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    listOf(
                        project.workOrder.ifBlank { "未绑定工单" },
                        project.manufacturer.ifBlank { "未填厂家" },
                        project.projectName.ifBlank { "未命名" },
                        selectedModel.folderName,
                        selectedConfig.displayLabel
                    ).joinToString("｜"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "已有照片项 $itemsWithPhotos / ${TestItems.size}" + if (retakeMode) "｜补拍模式" else "",
                    color = Color(0xFF617066),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TestGroups.forEach { group ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TestGroupHeader(group.title)
                }
                if (group.items.size == 1) {
                    val item = group.items.first()
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TestGridItem(
                            item = item,
                            photoCount = photoCounts[item.folderName] ?: 0,
                            onClick = { onOpenCapture(item) }
                        )
                    }
                } else {
                    gridItems(group.items) { item ->
                        TestGridItem(
                            item = item,
                            photoCount = photoCounts[item.folderName] ?: 0,
                            onClick = { onOpenCapture(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TestGroupHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(EmcPrimary)
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TestGridItem(item: TestItem, photoCount: Int, onClick: () -> Unit) {
    val hasPhotos = photoCount > 0
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .border(1.dp, Color(0xFFE5E5E5), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (hasPhotos) EmcPrimary else Color(0xFFB8B8B8))
                )
                Spacer(Modifier.width(8.dp))
                Text("${item.number} ${item.code}", fontWeight = FontWeight.Bold, color = Color(0xFF202020))
            }
            Text("${photoCount}张", color = if (hasPhotos) EmcPrimaryDark else Color(0xFF666666))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptureScreen(
    padding: PaddingValues,
    project: ProjectConfig,
    model: ModelConfig,
    config: ConfigOption,
    item: TestItem,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val photoCategories = photoCategoriesFor(item)
    var selectedCategory by remember(item.code) { mutableStateOf(photoCategories.first()) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var targetRotation by remember { mutableStateOf(Surface.ROTATION_0) }
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(context, hasCameraPermission) {
        if (!hasCameraPermission) return@DisposableEffect onDispose {}

        val listener = object : OrientationEventListener(context.applicationContext) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val nextRotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                if (targetRotation != nextRotation) {
                    targetRotation = nextRotation
                    imageCapture?.targetRotation = nextRotation
                    EmcLog.i("Capture target rotation updated: $nextRotation")
                }
            }
        }
        if (listener.canDetectOrientation()) {
            listener.enable()
        } else {
            EmcLog.w("Device cannot detect orientation for capture rotation")
        }
        onDispose { listener.disable() }
    }

    LaunchedEffect(imageCapture, targetRotation) {
        imageCapture?.targetRotation = targetRotation
    }

    DisposableEffect(imageCapture, project, model, config, item, selectedCategory) {
        VolumeShutter.action = {
            imageCapture?.let { capture ->
                takePhoto(
                    context = context,
                    imageCapture = capture,
                    project = project,
                    model = model,
                    item = item,
                    config = config,
                    category = selectedCategory
                )
            }
        }
        onDispose {
            VolumeShutter.action = null
        }
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .background(Color(0xFF101512))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.78f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("返回", color = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${item.number} ${item.code}", color = Color.White, fontWeight = FontWeight.Bold)
                Text("${model.folderName} / ${config.displayLabel} / 主摄 1x", color = Color(0xFFB8C7BD), style = MaterialTheme.typography.labelMedium)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF314038), RoundedCornerShape(8.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission) {
                CameraPreview(
                    targetRotation = targetRotation,
                    onImageCaptureReady = { imageCapture = it }
                )
            } else {
                Text("需要相机权限", color = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF7F8F8))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    val capture = imageCapture ?: return@Button
                    takePhoto(
                        context = context,
                        imageCapture = capture,
                        project = project,
                        model = model,
                        item = item,
                        config = config,
                        category = selectedCategory
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmcPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("拍照并保存到 ${item.folderName}")
            }
            Text("照片类别", fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                photoCategories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    targetRotation: Int,
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { ContextCompat.getMainExecutor(context) }

    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                bindCamera(
                    context = context,
                    previewView = this,
                    executor = executor,
                    targetRotation = targetRotation,
                    onImageCaptureReady = onImageCaptureReady
                ) { selector, preview, imageCapture ->
                    ProcessCameraProvider.getInstance(context).get().bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageCapture
                    )
                }
            }
        }
    )
}

private fun bindCamera(
    context: Context,
    previewView: PreviewView,
    executor: Executor,
    targetRotation: Int,
    onImageCaptureReady: (ImageCapture) -> Unit,
    bind: (CameraSelector, Preview, ImageCapture) -> Camera
) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        cameraProvider.unbindAll()
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        EmcLog.i("Binding camera: main rear 1x")
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(targetRotation)
            .build()
        bind(selector, preview, imageCapture)
        onImageCaptureReady(imageCapture)
    }, executor)
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    project: ProjectConfig,
    model: ModelConfig,
    item: TestItem,
    config: ConfigOption,
    category: String
) {
    val spec = buildPhotoSpec(project, model, item, config, category)

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, spec.fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, spec.relativePath)
        }
    ).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                EmcLog.i("Photo saved: ${spec.relativePath}/${spec.fileName}")
                Toast.makeText(context, "已保存：${spec.fileName}", Toast.LENGTH_SHORT).show()
            }

            override fun onError(exception: ImageCaptureException) {
                EmcLog.e("Photo save failed: ${spec.relativePath}/${spec.fileName}", exception)
                Toast.makeText(context, "保存失败：${exception.message}", Toast.LENGTH_LONG).show()
            }
        }
    )
}

private fun buildPhotoSpec(
    project: ProjectConfig,
    model: ModelConfig,
    item: TestItem,
    config: ConfigOption,
    category: String
): PhotoSpec {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
    val nameParts = listOfNotNull(
        config.runMode.toOptionalFileNamePart(),
        config.power.toOptionalFileNamePart(),
        testAxisFor(item, category).toOptionalFileNamePart(),
        model.name.toOptionalFileNamePart(),
        config.probe.toOptionalFileNamePart(),
        category.toOptionalFileNamePart(),
        timestamp
    )
    val fileName = "${nameParts.joinToString("_") { it.toFileNamePart() }}.jpg"
    val relativePath = listOf(
        Environment.DIRECTORY_PICTURES,
        "EMC现场助手",
        project.folderName.ifBlank { "未命名工单" },
        modelSubfolder(project, model),
        item.folderName
    ).filter { it.isNotBlank() }.joinToString("/")
    return PhotoSpec(fileName = fileName, relativePath = relativePath)
}

private fun photoCategoriesFor(item: TestItem): List<String> {
    return when (item.code) {
        "RE", "REHF", "RS" -> listOf("极性V", "极性H", "局部", "异常")
        "CE" -> listOf("相位N", "相位L", "局部", "异常")
        else -> listOf("全景", "局部", "异常")
    }
}

private fun testAxisFor(item: TestItem, category: String): String {
    return when {
        item.code in setOf("RE", "REHF", "RS") && category.startsWith("极性") -> "${item.code}${category}"
        item.code == "CE" && category.startsWith("相位") -> "${item.code}${category}"
        else -> item.code
    }
}

private fun previewSavePath(project: ProjectConfig, model: ModelConfig): String {
    return listOf(
        Environment.DIRECTORY_PICTURES,
        "EMC现场助手",
        project.folderName.ifBlank { "未命名工单" },
        modelSubfolder(project, model)
    ).filter { it.isNotBlank() }.joinToString("/") + "/"
}

private fun modelSubfolder(project: ProjectConfig, model: ModelConfig): String {
    return if (project.hasMultipleRoleModelCombos()) model.folderName else ""
}

private fun loadPhotoCounts(
    context: Context,
    project: ProjectConfig,
    model: ModelConfig
): Map<String, Int> {
    val counts = LinkedHashMap<String, Int>()
    TestItems.forEach { counts[it.folderName] = 0 }

    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
    val projectRoot = previewSavePath(project, model)

    context.contentResolver.query(
        collection,
        projection,
        "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
        arrayOf("$projectRoot%"),
        null
    )?.use { cursor ->
        val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
        while (cursor.moveToNext()) {
            val relativePath = cursor.getString(pathColumn) ?: continue
            val item = TestItems.firstOrNull { relativePath.contains("/${it.folderName}/") } ?: continue
            counts[item.folderName] = (counts[item.folderName] ?: 0) + 1
        }
    }

    return counts
}

private fun parseProjectJson(context: Context, uri: Uri): ProjectConfig? {
    EmcLog.i("Parsing Project JSON: $uri")
    val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ?: return null
    return parseProjectJsonText(text)
}

private fun parseProjectJsonText(text: String): ProjectConfig? {
    return runCatching {
        val root = JSONObject(text)
        val projectObject = root.firstObject("project", "projectInfo", "info") ?: root
        val samplesArray = projectObject.firstArray("samples", "models") ?: root.firstArray("samples", "models")
        val firstSample = samplesArray?.optJSONObject(0)
        val schemaVersion = root.firstString("schemaVersion", "schema_version", "version")
        val workOrder = projectObject.firstString("order_no", "workOrder", "orderNo", "orderNumber", "jobNumber", "reportNo", "单号")
            .ifBlank { root.firstString("order_no", "workOrder", "orderNo", "orderNumber", "jobNumber", "reportNo", "单号") }
        val manufacturer = projectObject.firstString("manufacturer", "maker", "customer", "client", "vendor", "厂家")
            .ifBlank { root.firstString("manufacturer", "maker", "customer", "client", "vendor", "厂家") }
            .ifBlank { firstSample?.firstString("manufacturer", "maker", "customer", "client", "vendor", "厂家").orEmpty() }
        val projectName = projectObject.firstString("sample_name", "projectName", "sampleName", "deviceName", "productName", "equipmentName", "name", "样品名称")
            .ifBlank { root.firstString("sample_name", "projectName", "sampleName", "deviceName", "productName", "equipmentName", "name", "样品名称") }
            .ifBlank { firstSample?.firstString("sample_name", "sampleName", "projectName", "deviceName", "productName", "equipmentName", "name", "样品名称").orEmpty() }

        val models = parseModelsFromProjectJson(root, projectObject)
        ProjectConfig(
            workOrder = workOrder,
            manufacturer = manufacturer,
            projectName = projectName,
            models = models.ifEmpty {
                listOf(
                    ModelConfig(
                        role = "主检",
                        name = projectObject.firstString("model", "modelName", "sampleModel", "型号").ifBlank { "A" }
                    )
                )
            }
        ).also {
            EmcLog.i("Project JSON parsed: schemaVersion=$schemaVersion, workOrder=$workOrder, manufacturer=$manufacturer, projectName=$projectName, models=${it.models.size}")
        }
    }.onFailure {
        EmcLog.e("Parse Project JSON failed", it)
    }.getOrNull()
}

private fun parseModelsFromProjectJson(root: JSONObject, projectObject: JSONObject): List<ModelConfig> {
    val array = projectObject.firstArray("samples", "models")
        ?: root.firstArray("samples", "models")
        ?: return emptyList()

    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val source = item.firstObject("model", "sample", "info") ?: item
        val role = source.firstString("sample_role", "role", "sampleRole", "mainCoverage", "type", "主检覆盖")
            .ifBlank { if (index == 0) "主检" else "覆盖" }
        val name = source.firstString("sample_model", "name", "model", "modelName", "sampleModel", "型号")
            .ifBlank { return@mapNotNull null }
        val configs = parseConfigOptions(source)
            .distinct()
        ModelConfig(role = role, name = name, configs = configs)
    }
}

private fun parseConfigOptions(sample: JSONObject): List<ConfigOption> {
    val configs = sample.optJSONArray("configs")
    if (configs != null && configs.length() > 0) {
        return (0 until configs.length()).mapNotNull { index ->
            val config = configs.optJSONObject(index)
            if (config != null) {
                buildConfigOption(sample, config)
            } else {
                configs.optString(index, "").trim().takeIf { it.isNotBlank() && it != "null" }
                    ?.let { ConfigOption(label = normalizeProbeConfig(it), probe = normalizeProbeConfig(it)) }
            }
        }.ifEmpty { listOf(ConfigOption()) }
    }

    return sample.firstStringArray("probeConfigs", "probes")
        .map { ConfigOption(label = normalizeProbeConfig(it), probe = normalizeProbeConfig(it)) }
        .ifEmpty { listOf(buildConfigOption(sample, null)) }
}

private fun buildConfigOption(sample: JSONObject, config: JSONObject?): ConfigOption {
    fun field(vararg names: String): String {
        return config?.firstString(*names).orEmpty().ifBlank { sample.firstString(*names) }
    }

    val probe = field("config_probe", "probeConfig", "probe", "config", "配置探头").trim()
    val power = field("power", "powerSupply", "电源").trim()
    val runMode = field("run_mode", "runMode", "mode", "运行模式").trim()
    val label = listOf(probe, power, runMode)
        .filter { it.isNotBlank() && it != "无" && it != "/" }
        .joinToString(" / ")
        .ifBlank { "默认" }

    return ConfigOption(label = label, probe = probe, power = power, runMode = runMode)
}

private fun JSONObject.firstObject(vararg names: String): JSONObject? {
    return names.firstNotNullOfOrNull { name -> optJSONObject(name) }
}

private fun JSONObject.firstArray(vararg names: String): JSONArray? {
    return names.firstNotNullOfOrNull { name -> optJSONArray(name) }
}

private fun JSONObject.firstString(vararg names: String): String {
    return names.firstNotNullOfOrNull { name ->
        optString(name, "").trim().takeIf { it.isNotBlank() && it != "null" }
    }.orEmpty()
}

private fun JSONObject.firstStringArray(vararg names: String): List<String> {
    val array = firstArray(*names) ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optString(index, "").trim().takeIf { it.isNotBlank() && it != "null" }
    }
}

private fun normalizeProbeConfig(value: String): String {
    val trimmed = value.trim()
    return if (trimmed.isBlank() || trimmed == "无" || trimmed == "/") "默认" else trimmed
}

private fun parseProjectFolder(context: Context, treeUri: Uri): ProjectConfig? {
    EmcLog.i("Parsing project folder: $treeUri")
    return runCatching {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val root = readDocumentNode(context, treeUri, treeDocumentId) ?: return null
        if (root.mimeType != DocumentsContract.Document.MIME_TYPE_DIR) return null

        val rootChildren = listChildDocuments(context, treeUri, root.documentId)
        rootChildren
            .filter { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR && it.name.endsWith(".json", ignoreCase = true) }
            .sortedBy { if (it.name.equals("project.json", ignoreCase = true)) 0 else 1 }
            .firstOrNull()
            ?.let { jsonNode ->
                readDocumentText(context, treeUri, jsonNode.documentId)
                    ?.let(::parseProjectJsonText)
                    ?.also {
                        EmcLog.i("Project folder parsed from JSON: ${jsonNode.name}")
                        return it
                    }
            }

        val folderInfo = parseProjectFolderName(root.name)
        val modelFolders = rootChildren
            .filter { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }
            .filter { child -> TestItems.none { it.folderName == child.name } }

        val models = if (modelFolders.isEmpty()) {
            val configs = linkedSetOf<ConfigOption>()
            val testFolders = rootChildren
                .filter { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }
                .filter { child -> TestItems.any { it.folderName == child.name } }
            testFolders.forEach { testFolder ->
                listChildDocuments(context, treeUri, testFolder.documentId)
                    .filter { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR }
                    .mapNotNull { inferConfigFromPhotoFileName(it.name) }
                    .forEach { configs.add(ConfigOption(label = it, probe = it)) }
            }
            if (configs.isEmpty()) configs.add(ConfigOption())
            listOf(ModelConfig("主检", folderInfo.modelName.ifBlank { "未填型号" }, configs = configs.toList()))
        } else {
            modelFolders.mapNotNull { modelFolder ->
                val (role, modelName) = parseModelFolderName(modelFolder.name)
                if (role.isBlank() || modelName.isBlank()) return@mapNotNull null

                val configs = linkedSetOf<ConfigOption>()
                val testFolders = listChildDocuments(context, treeUri, modelFolder.documentId)
                    .filter { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }
                    .filter { child -> TestItems.any { it.folderName == child.name } }

                testFolders.forEach { testFolder ->
                    listChildDocuments(context, treeUri, testFolder.documentId)
                        .filter { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR }
                        .mapNotNull { inferConfigFromPhotoFileName(it.name) }
                        .forEach { configs.add(ConfigOption(label = it, probe = it)) }
                }
                if (configs.isEmpty()) configs.add(ConfigOption())

                ModelConfig(role = role, name = modelName, configs = configs.toList())
            }.ifEmpty {
                listOf(ModelConfig("主检", folderInfo.modelName.ifBlank { "未填型号" }))
            }
        }

        ProjectConfig(
            workOrder = folderInfo.workOrder,
            manufacturer = folderInfo.manufacturer,
            projectName = folderInfo.sampleName,
            models = models
        )
            .also {
                EmcLog.i("Project folder parsed: workOrder=${folderInfo.workOrder}, manufacturer=${folderInfo.manufacturer}, projectName=${folderInfo.sampleName}, models=${models.size}")
            }
    }.onFailure {
        EmcLog.e("Parse project folder failed", it)
    }.getOrNull()
}

private data class DocumentNode(
    val documentId: String,
    val name: String,
    val mimeType: String
)

private fun readDocumentNode(context: Context, treeUri: Uri, documentId: String): DocumentNode? {
    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
    return context.contentResolver.query(
        documentUri,
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        ),
        null,
        null,
        null
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        DocumentNode(
            documentId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)),
            name = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) ?: "",
            mimeType = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)) ?: ""
        )
    }
}

private fun readDocumentText(context: Context, treeUri: Uri, documentId: String): String? {
    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
    return context.contentResolver.openInputStream(documentUri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
}

private fun listChildDocuments(context: Context, treeUri: Uri, parentDocumentId: String): List<DocumentNode> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
    val children = mutableListOf<DocumentNode>()
    context.contentResolver.query(
        childrenUri,
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        ),
        null,
        null,
        null
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
        while (cursor.moveToNext()) {
            children.add(
                DocumentNode(
                    documentId = cursor.getString(idColumn),
                    name = cursor.getString(nameColumn) ?: "",
                    mimeType = cursor.getString(mimeColumn) ?: ""
                )
            )
        }
    }
    return children
}

private data class ProjectFolderInfo(
    val workOrder: String,
    val manufacturer: String,
    val modelName: String,
    val sampleName: String
)

private fun parseProjectFolderName(folderName: String): ProjectFolderInfo {
    val parts = folderName.trim().split("_", limit = 4)
    if (parts.size >= 4) {
        return ProjectFolderInfo(
            workOrder = parts[0],
            manufacturer = parts[1],
            modelName = parts[2],
            sampleName = parts[3]
        )
    }
    val spaceParts = folderName.trim().split(Regex("\\s+"), limit = 4)
    if (spaceParts.size >= 4) {
        return ProjectFolderInfo(
            workOrder = spaceParts[0],
            manufacturer = spaceParts[1],
            modelName = spaceParts[2],
            sampleName = spaceParts[3]
        )
    }
    val legacyParts = folderName.split("_", limit = 2)
    return ProjectFolderInfo(
        workOrder = legacyParts.firstOrNull().orEmpty(),
        manufacturer = "",
        modelName = "未填型号",
        sampleName = legacyParts.getOrNull(1).orEmpty()
    )
}

private fun parseModelFolderName(folderName: String): Pair<String, String> {
    val parts = folderName.trim().split("_", limit = 2)
    if (parts.size >= 2) {
        return parts[0].ifBlank { "主检" } to parts[1].ifBlank { "A" }
    }
    val spaceParts = folderName.trim().split(Regex("\\s+"), limit = 2)
    return spaceParts.firstOrNull().orEmpty().ifBlank { "主检" } to spaceParts.getOrNull(1).orEmpty().ifBlank { "A" }
}

private fun inferConfigFromPhotoFileName(fileName: String): String? {
    val baseName = fileName.substringBeforeLast('.', fileName)
    val parts = baseName.split("_")
    if (parts.size < 4) return null
    val categoryIndex = parts.indexOfLast { it in KnownPhotoCategoriesForImport }
    if (categoryIndex < 0 || categoryIndex + 2 >= parts.size) return null
    if (!parts[categoryIndex + 1].matches(Regex("\\d{8}"))) return null
    if (!parts[categoryIndex + 2].matches(Regex("\\d{6}"))) return null
    if (categoryIndex <= 1) return "默认"
    return parts.subList(1, categoryIndex).joinToString("_").ifBlank { "默认" }
}

private val KnownPhotoCategoriesForImport = listOf("全景", "局部", "极性V", "极性H", "相位N", "相位L", "异常", "局部细节", "其他")

private fun String.sanitizePathPart(): String {
    return trim()
        .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_")
        .replace(Regex("\\s+"), " ")
        .ifBlank { "未命名" }
}

private fun String.toFileNamePart(): String {
    return trim()
        .replace(Regex("[\\\\/:*?\"<>|_\\n\\r\\t]"), " ")
        .replace(Regex("\\s+"), " ")
        .ifBlank { "未指定" }
}

private fun String.toOptionalFileNamePart(): String? {
    val normalized = trim()
    if (normalized.isBlank() || normalized == "无" || normalized == "/" || normalized == "默认" || normalized == "未指定") {
        return null
    }
    return normalized.toFileNamePart()
}
