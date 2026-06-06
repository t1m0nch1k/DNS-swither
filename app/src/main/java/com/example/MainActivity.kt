package com.example

import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.VpnService
import android.app.Activity
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val SophisticatedDarkScheme = darkColorScheme(
                primary = Color(0xFFD0BCFF),
                onPrimary = Color(0xFF381E72),
                primaryContainer = Color(0xFFD0BCFF),
                onPrimaryContainer = Color(0xFF381E72),
                background = Color(0xFF0D0D0D),
                onBackground = Color(0xFFE6E1E5),
                surface = Color(0xFF1C1B1F),
                onSurface = Color(0xFFE6E1E5),
                surfaceVariant = Color(0xFF25232A),
                onSurfaceVariant = Color(0xFFE6E1E5),
                outline = Color(0xFF35333B),
                outlineVariant = Color(0xFF302D33),
                error = Color(0xFFFFB4AB),
                onError = Color(0xFF690005)
            )
            MaterialTheme(colorScheme = SophisticatedDarkScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val context = LocalContext.current
                    val database = remember { AppDatabase.getDatabase(context) }
                    val repository = remember { DnsRepository(database.dnsDao()) }
                    val viewModel: DnsViewModel = viewModel(factory = DnsViewModelFactory(repository, context))
                    
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                viewModel.refreshStatus()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    MainScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: DnsViewModel) {
    val servers by viewModel.servers.collectAsState()
    val currentDns by viewModel.currentDns.collectAsState()
    val engineMode by viewModel.engineMode.collectAsState()
    val isDeviceOwner by viewModel.isDeviceOwner.collectAsState()
    val hasWriteSecure by viewModel.hasWriteSecure.collectAsState()
    val lastError by viewModel.lastError.collectAsState()
    val pendingVpnServer by viewModel.pendingVpnServer.collectAsState()
    val context = LocalContext.current

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.onVpnPermissionGranted()
            } else {
                viewModel.onVpnPermissionDenied()
            }
        }
    )

    LaunchedEffect(pendingVpnServer) {
        pendingVpnServer?.let {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                vpnLauncher.launch(intent)
            } else {
                viewModel.onVpnPermissionGranted()
            }
        }
    }

    LaunchedEffect(lastError) {
        lastError?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("DNS Controller", fontWeight = FontWeight.SemiBold, color = Color.White)
                        val activeText = when {
                            engineMode == DnsPreferences.ENGINE_PRIVATE_DNS && isDeviceOwner -> "PRIVATE DNS (DEVICE OWNER)"
                            engineMode == DnsPreferences.ENGINE_PRIVATE_DNS && hasWriteSecure -> "PRIVATE DNS (SECURE WRITE)"
                            engineMode == DnsPreferences.ENGINE_VPN -> "ОСНОВНОЙ VPN ДВИЖОК АКТИВЕН"
                            else -> null
                        }
                        if (activeText != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF34D399)))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(activeText, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399), letterSpacing = 1.sp)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp).navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier.weight(1f).height(72.dp).clickable { viewModel.deactivateDns() },
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp).fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Мастер-выключатель", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        Switch(
                            checked = currentDns == null,
                            onCheckedChange = { if (it) viewModel.deactivateDns() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.onPrimary,
                                uncheckedThumbColor = Color(0xFF938F99),
                                uncheckedTrackColor = MaterialTheme.colorScheme.outline,
                                uncheckedBorderColor = Color.Transparent,
                                checkedBorderColor = Color.Transparent
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add DNS", modifier = Modifier.size(32.dp))
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (engineMode == DnsPreferences.ENGINE_PRIVATE_DNS && !isDeviceOwner && !hasWriteSecure) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4A1414)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Требуются права доступа", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Для Private DNS режима требуются дополнительные полномочия. Так как на вашем устройстве уже есть Google/Samsung аккаунты, Android блокирует установку Device Owner без полного сброса. Но вы можете выдать права напрямую (без всякого сброса данных) простой командой через ADB:\n\n" +
                            "Рекомендуемая команда:\n" +
                            ".\\adb shell pm grant com.aistudio.privatedns.swrxtw android.permission.WRITE_SECURE_SETTINGS\n\n" +
                            "Скопируйте и выполните её в консоли PowerShell/cmd, после чего нажмите кнопку ниже. Либо переключитесь на режим Primary VPN, работающий без ADB.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.refreshStatus()
                                val isGranted = DnsController.hasWriteSecureSettings(context)
                                val statusText = if (isGranted) {
                                    "Успешно: Права WRITE_SECURE_SETTINGS получены! Ограничение снято."
                                } else {
                                    "Ошибка: Права всё ещё не обнаружены. Убедитесь, что команда выполнена именно для этого устройства и приложение открыто."
                                }
                                android.widget.Toast.makeText(context, statusText, android.widget.Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF4A1414)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("ПРОВЕРИТЬ СТАТУС", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            val activeServer = servers.find { it.hostname == currentDns }

            if (activeServer != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("АКТИВНЫЙ ПРОВАЙДЕР", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(activeServer.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                val detailValue = if (engineMode == DnsPreferences.ENGINE_VPN) activeServer.ipAddress else activeServer.hostname
                                Text(detailValue, fontSize = 14.sp, color = Color(0xFF938F99), fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline)) {
                            Box(modifier = Modifier.fillMaxWidth(1f).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("DNS ВЫКЛЮЧЕН", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF938F99), letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Нет активного провайдера", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Запросы используют стандартные маршруты системы", fontSize = 14.sp, color = Color(0xFF938F99))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("ДОПОЛНИТЕЛЬНЫЕ НАСТРОЙКИ", color = Color(0xFF938F99), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.setEngineMode(DnsPreferences.ENGINE_VPN) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = engineMode == DnsPreferences.ENGINE_VPN,
                            onClick = { viewModel.setEngineMode(DnsPreferences.ENGINE_VPN) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Primary Engine (Local VPN)", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Redirects port 53. Out-of-the-box, no ADB needed.", fontSize = 11.sp, color = Color(0xFF938F99))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.setEngineMode(DnsPreferences.ENGINE_PRIVATE_DNS) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = engineMode == DnsPreferences.ENGINE_PRIVATE_DNS,
                            onClick = { viewModel.setEngineMode(DnsPreferences.ENGINE_PRIVATE_DNS) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Secondary Engine (Private DNS)", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Android native system routing. Requires Device Owner/ADB.", fontSize = 11.sp, color = Color(0xFF938F99))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("ДОСТУПНЫЕ ПРОВАЙДЕРЫ", color = Color(0xFF938F99), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(servers) { server ->
                    val isActive = currentDns == server.hostname
                    ServerItem(
                        server = server,
                        isActive = isActive,
                        engineMode = engineMode,
                        onClick = { viewModel.activateDns(server) },
                        onDelete = { viewModel.deleteServer(server) }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddServerDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { label, hostname, ipAddress ->
                    viewModel.addServer(label, hostname, ipAddress)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun ServerItem(server: DnsServer, isActive: Boolean, engineMode: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outline),
                contentAlignment = Alignment.Center
            ) {
                 val initial = server.label.firstOrNull()?.uppercase() ?: "S"
                 Text(initial, fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                     server.label,
                     fontWeight = FontWeight.SemiBold,
                     color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface
                )
                val subtitle = if (engineMode == DnsPreferences.ENGINE_VPN) server.ipAddress else server.hostname
                Text(
                     subtitle,
                     style = MaterialTheme.typography.bodySmall,
                     color = Color(0xFF938F99),
                     maxLines = 1,
                     overflow = TextOverflow.Ellipsis
                )
            }
            if (isActive) {
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary))
                }
            } else {
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).border(2.dp, Color(0xFF938F99), CircleShape))
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFF938F99), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun AddServerDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var label by remember { mutableStateOf("") }
    var hostname by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom DNS") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = hostname,
                    onValueChange = { 
                        hostname = it
                        errorMessage = null 
                    },
                    label = { Text("Hostname Specifier (DoT)") },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { 
                        ipAddress = it
                        errorMessage = null 
                    },
                    label = { Text("IP Address (VPN Mode)") },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val ipValid = Patterns.IP_ADDRESS.matcher(ipAddress).matches()
                val hostValid = Patterns.WEB_URL.matcher(hostname).matches() || hostname.matches(Regex("^[a-zA-Z0-9.-]+$"))
                if (label.isBlank() || hostname.isBlank() || ipAddress.isBlank()) {
                    errorMessage = "All fields are required"
                } else if (!hostValid) {
                    errorMessage = "Invalid hostname format"
                } else if (!ipValid) {
                    errorMessage = "Invalid IP address format"
                } else {
                    onAdd(label.trim(), hostname.trim(), ipAddress.trim())
                }
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
