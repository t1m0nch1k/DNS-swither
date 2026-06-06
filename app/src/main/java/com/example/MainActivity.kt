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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import androidx.compose.animation.core.*

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
                val backgroundGradient = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF14121F), // Deep cybernetic indigo
                        Color(0xFF07050C)  // Midnight space obsidian
                    )
                )
                Surface(
                    modifier = Modifier.fillMaxSize().background(backgroundGradient),
                    color = Color.Transparent
                ) {
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
    val firstPacketSize by viewModel.firstPacketSize.collectAsState()
    val enableFakePacket by viewModel.enableFakePacket.collectAsState()
    val enableCaseSpoof by viewModel.enableCaseSpoof.collectAsState()
    val routingMode by viewModel.routingMode.collectAsState()
    val routingApps by viewModel.routingApps.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val enableDoh by viewModel.enableDoh.collectAsState()
    val socks5Enabled by viewModel.socks5Enabled.collectAsState()
    val socks5Host by viewModel.socks5Host.collectAsState()
    val socks5Port by viewModel.socks5Port.collectAsState()

    val wgWarpEnabled by viewModel.wgWarpEnabled.collectAsState()
    val wgPrivateKey by viewModel.wgPrivateKey.collectAsState()
    val wgClientIp by viewModel.wgClientIp.collectAsState()
    val wgPeerPubkey by viewModel.wgPeerPubkey.collectAsState()
    val wgEndpoint by viewModel.wgEndpoint.collectAsState()

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
    var showAppSelectorDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("DNS Контроллер", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 20.sp)
                        val activeText = when {
                            engineMode == DnsPreferences.ENGINE_PRIVATE_DNS && isDeviceOwner -> "PRIVATE DNS (DEVICE OWNER)"
                            engineMode == DnsPreferences.ENGINE_PRIVATE_DNS && hasWriteSecure -> "PRIVATE DNS (SECURE WRITE)"
                            engineMode == DnsPreferences.ENGINE_VPN -> "АКТИВЕН ДВИЖОК VPN"
                            else -> null
                        }
                        if (activeText != null && currentDns != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                PulsingDot(color = Color(0xFF34D399), modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(activeText, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399), letterSpacing = 1.sp)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить провайдер")
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 1. Warning card if permissions are missing for Secure Write System settings
            if (engineMode == DnsPreferences.ENGINE_PRIVATE_DNS && !isDeviceOwner && !hasWriteSecure) {
                item {
                    DeviceOwnerWarningCard(
                        packageName = context.packageName,
                        onRefresh = { viewModel.refreshStatus() }
                    )
                }
            }

            // 2. Active status panel (Dashboard)
            item {
                val activeServer = servers.find { it.hostname == currentDns }
                ProtectionDashboardCard(
                    isActive = currentDns != null,
                    activeServerLabel = activeServer?.label,
                    activeServerHost = activeServer?.hostname ?: currentDns,
                    engineMode = engineMode,
                    onToggle = { enableProtect ->
                        if (enableProtect) {
                            val activeServerObj = activeServer ?: servers.firstOrNull() ?: DnsServer(
                                label = "Cloudflare",
                                hostname = "1dot1dot1dot1.cloudflare-dns.com",
                                ipAddress = "1.1.1.1"
                            )
                            viewModel.activateDns(activeServerObj)
                        } else {
                            viewModel.deactivateDns()
                        }
                    }
                )
            }

            // 3. Choice of Route Mode
            item {
                SectionTitle("РЕЖИМ РАБОТЫ (ДВИЖОК)")
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EngineCard(
                        title = "Движок VPN",
                        description = "Локальная переадресация (Port 53). Работает сразу, рут и ADB не нужны.",
                        isSelected = engineMode == DnsPreferences.ENGINE_VPN,
                        onClick = { viewModel.setEngineMode(DnsPreferences.ENGINE_VPN) },
                        modifier = Modifier.weight(1f)
                    )
                    EngineCard(
                        title = "Системный DoT",
                        description = "Встроенный Private DNS. Требует Device Owner или выдачи WRITE_SECURE через ADB.",
                        isSelected = engineMode == DnsPreferences.ENGINE_PRIVATE_DNS,
                        onClick = { viewModel.setEngineMode(DnsPreferences.ENGINE_PRIVATE_DNS) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3.5 YouTube & Discord Preset Card
            item {
                SectionTitle("БЫСТРАЯ НАСТРОЙКА YOUTUBE / DISCORD")
                Spacer(modifier = Modifier.height(2.dp))
                YouTubePresetCard(
                    engineMode = engineMode,
                    enableFakePacket = enableFakePacket,
                    enableCaseSpoof = enableCaseSpoof,
                    firstPacketSize = firstPacketSize,
                    onApplyPreset = {
                        viewModel.setEngineMode(DnsPreferences.ENGINE_VPN)
                        viewModel.setEnableFakePacket(false)
                        viewModel.setEnableCaseSpoof(true)
                        viewModel.setFirstPacketSize(10)
                        
                        // If no DNS is active, activate Cloudflare
                        if (currentDns == null) {
                            val cloudflare = servers.find { it.hostname == "1dot1dot1dot1.cloudflare-dns.com" } 
                                ?: servers.firstOrNull() 
                                ?: DnsServer(label = "Cloudflare", hostname = "1dot1dot1dot1.cloudflare-dns.com", ipAddress = "1.1.1.1")
                            viewModel.activateDns(cloudflare)
                        }
                        android.widget.Toast.makeText(context, "Настройки для YouTube успешно применены!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 4. DPI Evasion Parameters
            item {
                SectionTitle("ОБХОД DPI БЛОКИРОВОК (SNI)")
                Spacer(modifier = Modifier.height(2.dp))
                DpiEvasionCard(
                    enableFakePacket = enableFakePacket,
                    onEnableFakeChange = { viewModel.setEnableFakePacket(it) },
                    enableCaseSpoof = enableCaseSpoof,
                    onEnableCaseChange = { viewModel.setEnableCaseSpoof(it) },
                    firstPacketSize = firstPacketSize,
                    onSizeChange = { viewModel.setFirstPacketSize(it) }
                )
            }

            // 4.5. Warp and SOCKS5 proxy settings
            item {
                SectionTitle("НАСТРОЙКА PROXY / CLOUDFLARE WARP")
                Spacer(modifier = Modifier.height(2.dp))
                WarpSocksCard(
                    enableDoh = enableDoh,
                    onEnableDohChange = { viewModel.setEnableDoh(it) },
                    socks5Enabled = socks5Enabled,
                    onSocks5EnabledChange = { viewModel.setSocks5Enabled(it) },
                    socks5Host = socks5Host,
                    onSocks5HostChange = { viewModel.setSocks5Host(it) },
                    socks5Port = socks5Port,
                    onSocks5PortChange = { viewModel.setSocks5Port(it) },
                    wgWarpEnabled = wgWarpEnabled,
                    onWgWarpEnabledChange = { viewModel.setWgWarpEnabled(it) },
                    wgPrivateKey = wgPrivateKey,
                    onWgPrivateKeyChange = { viewModel.setWgPrivateKey(it) },
                    wgClientIp = wgClientIp,
                    onWgClientIpChange = { viewModel.setWgClientIp(it) },
                    wgPeerPubkey = wgPeerPubkey,
                    onWgPeerPubkeyChange = { viewModel.setWgPeerPubkey(it) },
                    wgEndpoint = wgEndpoint,
                    onWgEndpointChange = { viewModel.setWgEndpoint(it) },
                    onGenerateWarpProfile = { onSuccess, onError ->
                        viewModel.generateWarpProfile(onSuccess, onError)
                    }
                )
            }

            // 5. App routing controls (VPN filters)
            item {
                SectionTitle("МАРШРУТИЗАЦИЯ ТРАФИКА (VPN)")
                Spacer(modifier = Modifier.height(2.dp))
                AppRoutingCard(
                    routingMode = routingMode,
                    onModeChange = { viewModel.setRoutingMode(it) },
                    routingAppsSize = routingApps.size,
                    onManageAppsClick = { showAppSelectorDialog = true }
                )
            }

            // 6. Available Providers List Headers
            item {
                SectionTitle("ДОСТУПНЫЕ DNS ПРОВАЙДЕРЫ")
            }

            items(servers, key = { it.id }) { server ->
                val isActive = currentDns == server.hostname
                ServerItem(
                    server = server,
                    isActive = isActive,
                    engineMode = engineMode,
                    onClick = { viewModel.activateDns(server) },
                    onDelete = { viewModel.deleteServer(server) }
                )
            }

            // Margin space
            item {
                Spacer(modifier = Modifier.height(96.dp))
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

        if (showAppSelectorDialog) {
            AppSelectorDialog(
                installedApps = installedApps,
                routingApps = routingApps,
                onToggle = { viewModel.toggleAppRouting(it) },
                onDismiss = { showAppSelectorDialog = false }
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha * 0.4f))
                .align(Alignment.Center)
                .graphicsLayer(scaleX = scale, scaleY = scale)
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun CustomDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
fun EngineCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .border(1.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF938F99), CircleShape)
                        .padding(3.dp)
                ) {
                    if (isSelected) {
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary, CircleShape))
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(description, fontSize = 11.sp, color = Color(0xFF938F99), lineHeight = 14.sp)
        }
    }
}

@Composable
fun ProtectionDashboardCard(
    isActive: Boolean,
    activeServerLabel: String?,
    activeServerHost: String?,
    engineMode: String,
    onToggle: (Boolean) -> Unit
) {
    val statusColor = if (isActive) Color(0xFF34D399) else Color(0xFF938F99)
    val cardBg = Brush.linearGradient(
        colors = if (isActive) {
            listOf(Color(0xFF211D3B), Color(0xFF141226))
        } else {
            listOf(Color(0xFF1C1B1F), Color(0xFF121115))
        }
    )
    val glowingOutline = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = glowingOutline
    ) {
        Box(
            modifier = Modifier
                .background(cardBg)
                .padding(24.dp)
        ) {
            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isActive) "ЗАЩИТА АКТИВНА" else "ЗАЩИТА ОТКЛЮЧЕНА",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isActive) (activeServerLabel ?: "Подключено") else "Трафик не защищен",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (isActive && activeServerHost != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Хост: $activeServerHost • ${if (engineMode == DnsPreferences.ENGINE_VPN) "VPN" else "DoT"}",
                                fontSize = 12.sp,
                                color = Color(0xFF938F99),
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Switch(
                        checked = isActive,
                        onCheckedChange = onToggle,
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
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    PulsingDot(color = statusColor)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isActive) {
                            "Приватные DNS-запросы перенаправляются по надежному каналу"
                        } else {
                            "Запросы проходят через незащищенные серверы провайдера"
                        },
                        fontSize = 11.sp,
                        color = Color(0xFFC4C4C4),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun DpiEvasionCard(
    enableFakePacket: Boolean,
    onEnableFakeChange: (Boolean) -> Unit,
    enableCaseSpoof: Boolean,
    onEnableCaseChange: (Boolean) -> Unit,
    firstPacketSize: Int,
    onSizeChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Фейковый TLS пакет", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text("Имитирует Client Hello для обхода DPI фильтров цензуры", fontSize = 11.sp, color = Color(0xFF938F99))
                }
                Switch(
                    checked = enableFakePacket,
                    onCheckedChange = onEnableFakeChange,
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
            
            Spacer(modifier = Modifier.height(14.dp))
            CustomDivider()
            Spacer(modifier = Modifier.height(14.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Спуфинг регистра SNI", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text("Чередует регистр букв домена для маскировки запроса", fontSize = 11.sp, color = Color(0xFF938F99))
                }
                Switch(
                    checked = enableCaseSpoof,
                    onCheckedChange = onEnableCaseChange,
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
            
            Spacer(modifier = Modifier.height(16.dp))
            CustomDivider()
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Фрагментация Client Hello", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Text("Размер первого фрагмента пакета в байтах", fontSize = 11.sp, color = Color(0xFF938F99))
                    }
                    Text(
                        text = "$firstPacketSize B",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Slider(
                    value = firstPacketSize.toFloat(),
                    onValueChange = { onSizeChange(it.toInt()) },
                    valueRange = 1f..1500f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(40, 100, 500, 1200).forEach { size ->
                        val isSelected = firstPacketSize == size
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .clickable { onSizeChange(size) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${size}B",
                                fontSize = 11.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WarpSocksCard(
    enableDoh: Boolean,
    onEnableDohChange: (Boolean) -> Unit,
    socks5Enabled: Boolean,
    onSocks5EnabledChange: (Boolean) -> Unit,
    socks5Host: String,
    onSocks5HostChange: (String) -> Unit,
    socks5Port: Int,
    onSocks5PortChange: (Int) -> Unit,
    wgWarpEnabled: Boolean,
    onWgWarpEnabledChange: (Boolean) -> Unit,
    wgPrivateKey: String,
    onWgPrivateKeyChange: (String) -> Unit,
    wgClientIp: String,
    onWgClientIpChange: (String) -> Unit,
    wgPeerPubkey: String,
    onWgPeerPubkeyChange: (String) -> Unit,
    wgEndpoint: String,
    onWgEndpointChange: (String) -> Unit,
    onGenerateWarpProfile: (() -> Unit, (String) -> Unit) -> Unit
) {
    var hostInput by remember(socks5Host) { mutableStateOf(socks5Host) }
    var portInput by remember(socks5Port) { mutableStateOf(socks5Port.toString()) }

    var privKeyVal by remember(wgPrivateKey) { mutableStateOf(wgPrivateKey) }
    var clientIpVal by remember(wgClientIp) { mutableStateOf(wgClientIp) }
    var peerKeyVal by remember(wgPeerPubkey) { mutableStateOf(wgPeerPubkey) }
    var endpointVal by remember(wgEndpoint) { mutableStateOf(wgEndpoint) }

    var isRegInProgress by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth().testTag("warp_socks_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Section 1: DoH (DNS Over HTTPS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Безопасный DNS (DoH)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text("Шифрует DNS через Cloudflare / Google для защиты от подмены провайдером", fontSize = 11.sp, color = Color(0xFF938F99))
                }
                Switch(
                    checked = enableDoh,
                    onCheckedChange = onEnableDohChange,
                    modifier = Modifier.testTag("doh_switch"),
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

            Spacer(modifier = Modifier.height(14.dp))
            CustomDivider()
            Spacer(modifier = Modifier.height(14.dp))

            // Section 2: Direct WireGuard Cloudflare WARP client
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Прямой Cloudflare WARP (WireGuard)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text("Подключается к WARP через WireGuard туннель нашей VPN для обхода DPI", fontSize = 11.sp, color = Color(0xFF938F99))
                }
                Switch(
                    checked = wgWarpEnabled,
                    onCheckedChange = {
                        onWgWarpEnabledChange(it)
                        if (it) {
                            onSocks5EnabledChange(false) // exclusive
                        }
                    },
                    modifier = Modifier.testTag("wg_warp_switch"),
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

            if (wgWarpEnabled) {
                Spacer(modifier = Modifier.height(14.dp))
                
                Button(
                    onClick = {
                        isRegInProgress = true
                        onGenerateWarpProfile(
                            {
                                isRegInProgress = false
                                android.widget.Toast.makeText(context, "Профиль WARP успешно создан!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            { error ->
                                isRegInProgress = false
                                android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("generate_warp_btn"),
                    enabled = !isRegInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (isRegInProgress) "Создание профиля..." else "Зарегистрировать профиль WARP",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = privKeyVal,
                        onValueChange = {
                            privKeyVal = it
                            onWgPrivateKeyChange(it)
                        },
                        label = { Text("Приватный ключ (Base64)", color = Color(0xFF938F99), fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("wg_priv_key_input"),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.OutlinedTextField(
                            value = clientIpVal,
                            onValueChange = {
                                clientIpVal = it
                                onWgClientIpChange(it)
                            },
                            label = { Text("Клиентский IP", color = Color(0xFF938F99), fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("wg_client_ip_input"),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )

                        androidx.compose.material3.OutlinedTextField(
                            value = endpointVal,
                            onValueChange = {
                                endpointVal = it
                                onWgEndpointChange(it)
                            },
                            label = { Text("Эндпоинт", color = Color(0xFF938F99), fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f).testTag("wg_endpoint_input"),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }

                    androidx.compose.material3.OutlinedTextField(
                        value = peerKeyVal,
                        onValueChange = {
                            peerKeyVal = it
                            onWgPeerPubkeyChange(it)
                        },
                        label = { Text("Публичный ключ сервера (Base64)", color = Color(0xFF938F99), fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("wg_peer_pubkey_input"),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            CustomDivider()
            Spacer(modifier = Modifier.height(14.dp))

            // Section 3: SOCKS5 Proxying
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SOCKS5 Проксирование", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text("Пропускает TCP трафик через SOCKS5 туннель стороннего приложения", fontSize = 11.sp, color = Color(0xFF938F99))
                }
                Switch(
                    checked = socks5Enabled,
                    onCheckedChange = {
                        onSocks5EnabledChange(it)
                        if (it) {
                            onWgWarpEnabledChange(false) // exclusive
                        }
                    },
                    modifier = Modifier.testTag("socks5_switch"),
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

            if (socks5Enabled) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = hostInput,
                        onValueChange = {
                            hostInput = it
                            onSocks5HostChange(it)
                        },
                        label = { Text("IP адрес / Хост", color = Color(0xFF938F99), fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(3f).testTag("socks5_host_input"),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    androidx.compose.material3.OutlinedTextField(
                        value = portInput,
                        onValueChange = {
                            portInput = it
                            val parsedPort = it.toIntOrNull()
                            if (parsedPort != null && parsedPort in 1..65535) {
                                onSocks5PortChange(parsedPort)
                            }
                        },
                        label = { Text("Порт", color = Color(0xFF938F99), fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f).testTag("socks5_port_input"),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Подсказка: Для работы с Cloudflare Warp запустите клиент Warp (1.1.1.1) в режиме прокси (обычно адрес 127.0.0.1 и порт 40000) либо укажите настройки стороннего SOCKS5 прокси.",
                    fontSize = 10.sp,
                    color = Color(0xFF8E8E93),
                    lineHeight = 13.sp
                )
            }
        }
    }
}

@Composable
fun AppRoutingCard(
    routingMode: String,
    onModeChange: (String) -> Unit,
    routingAppsSize: Int,
    onManageAppsClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Режим фильтрации", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    listOf(
                        "ALL" to "Все прил.",
                        "SELECTED" to "Выбранные",
                        "EXCLUDE" to "Исключить"
                    ).forEach { (mode, label) ->
                        val isSelected = routingMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { onModeChange(mode) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFC4C4C4),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            if (routingMode != "ALL") {
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onManageAppsClick,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Выбрать приложения ($routingAppsSize)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceOwnerWarningCard(
    packageName: String,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val adbCommand = ".\\adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E191D)),
        border = BorderStroke(1.dp, Color(0xFF6B2D37)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFFB4AB), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Требуются права доступа", fontWeight = FontWeight.Bold, color = Color(0xFFFFB4AB), fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Для включения Private DNS требуется выдать разрешения приложению напрямую через ADB (без сброса настроек). Подключите телефон к компьютеру и запустите команду:\n",
                color = Color(0xFFE6E1E5),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, Color(0xFF6B2D37), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = adbCommand,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFFDAD6),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(adbCommand))
                        android.widget.Toast.makeText(context, "Команда скопирована!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFFB4AB))
                ) {
                    Text("КОПИРОВАТЬ", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    onRefresh()
                    val isGranted = DnsController.hasWriteSecureSettings(context)
                    val statusText = if (isGranted) {
                        "Отлично! Разрешение WRITE_SECURE_SETTINGS успешно получено."
                    } else {
                        "Разрешение всё ещё отсутствует. Проверьте правильность подключения и повторите запуск команды."
                    }
                    android.widget.Toast.makeText(context, statusText, android.widget.Toast.LENGTH_LONG).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB4AB), contentColor = Color(0xFF690005)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Text("ПРОВЕРИТЬ СТАТУС", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ServerItem(
    server: DnsServer, 
    isActive: Boolean, 
    engineMode: String, 
    onClick: () -> Unit, 
    onDelete: () -> Unit
) {
    val borderColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val cardBg = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                 val initial = server.label.firstOrNull()?.uppercase() ?: "S"
                 Text(
                     text = initial, 
                     fontSize = 18.sp, 
                     color = if (isActive) MaterialTheme.colorScheme.onPrimary else Color.White, 
                     fontWeight = FontWeight.Bold
                 )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                     text = server.label,
                     fontWeight = FontWeight.Bold,
                     fontSize = 15.sp,
                     color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = if (engineMode == DnsPreferences.ENGINE_VPN) server.ipAddress else server.hostname
                Text(
                     text = subtitle,
                     fontSize = 12.sp,
                     color = Color(0xFF938F99),
                     maxLines = 1,
                     overflow = TextOverflow.Ellipsis,
                     fontFamily = FontFamily.Monospace
                )
            }
            
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle, 
                        contentDescription = "Active", 
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color(0xFF49454F), CircleShape)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            IconButton(
                onClick = onDelete, 
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.04f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete, 
                    contentDescription = "Delete", 
                    tint = Color(0xFFE27C7C), 
                    modifier = Modifier.size(18.dp)
                )
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
        title = { Text("Добавить DNS провайдер", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Название (например, Cloudflare Custom)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = hostname,
                    onValueChange = { 
                        hostname = it
                        errorMessage = null 
                    },
                    label = { Text("Хост DoT (например, dns.google)") },
                    singleLine = true,
                    isError = errorMessage != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { 
                        ipAddress = it
                        errorMessage = null 
                    },
                    label = { Text("IP адрес VPN (например, 8.8.8.8)") },
                    singleLine = true,
                    isError = errorMessage != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ipValid = Patterns.IP_ADDRESS.matcher(ipAddress).matches()
                    val hostValid = Patterns.WEB_URL.matcher(hostname).matches() || hostname.matches(Regex("^[a-zA-Z0-9.-]+$"))
                    if (label.isBlank() || hostname.isBlank() || ipAddress.isBlank()) {
                        errorMessage = "Все поля обязательны для заполнения"
                    } else if (!hostValid) {
                        errorMessage = "Неверный формат имени хоста"
                    } else if (!ipValid) {
                        errorMessage = "Неверный формат IP-адреса"
                    } else {
                        onAdd(label.trim(), hostname.trim(), ipAddress.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Добавить", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF938F99))) {
                Text("Отмена")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorDialog(
    installedApps: List<AppInfo>,
    routingApps: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        content = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF14121F),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Выбор приложений",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        }
                        Text(
                            "Выбрано: ${routingApps.size}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск по названию или пакету...", color = Color(0xFF938F99), fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF938F99))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Очистить", tint = Color(0xFF938F99))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box(modifier = Modifier.weight(1f)) {
                        if (filteredApps.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Приложения не найдены", color = Color(0xFF938F99), fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredApps, key = { it.packageName }) { app ->
                                    val isChecked = routingApps.contains(app.packageName)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent)
                                            .clickable { onToggle(app.packageName) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppIconImage(drawable = app.icon, modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                app.label,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                app.packageName,
                                                color = Color(0xFF938F99),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { onToggle(app.packageName) },
                                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Готово", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    )
}

@Composable
fun AppIconImage(drawable: android.graphics.drawable.Drawable, modifier: Modifier = Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            android.widget.ImageView(context).apply {
                setImageDrawable(drawable)
            }
        },
        modifier = modifier
    )
}

@Composable
fun YouTubePresetCard(
    engineMode: String,
    enableFakePacket: Boolean,
    enableCaseSpoof: Boolean,
    firstPacketSize: Int,
    onApplyPreset: () -> Unit
) {
    val isFullyConfigured = engineMode == DnsPreferences.ENGINE_VPN && enableFakePacket && enableCaseSpoof

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFullyConfigured) Color(0xFF13231C) else Color(0xFF26181A)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isFullyConfigured) Color(0xFF2E5E4E) else Color(0xFF5E2E31)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isFullyConfigured) Color(0xFF1B4D3E) else Color(0xFF4D1B1D)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "YouTube / Discord Bypass",
                        tint = if (isFullyConfigured) Color(0xFF34D399) else Color(0xFFF87171),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Оптимизация под YouTube / Discord",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (isFullyConfigured) "Параметры обхода успешно применены!" else "Желательно применить оптимальный профиль",
                        color = if (isFullyConfigured) Color(0xFF34D399) else Color(0xFFF87171),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            CustomDivider()
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Для стабильной работы YouTube и Discord в обход средств ТСПУ / DPI вашего провайдера необходимы следующие параметры:",
                color = Color(0xFFE6E1E5),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            val steps = listOf(
                "Движок: VPN (системный DoT не поддерживает обход DPI)" to (engineMode == DnsPreferences.ENGINE_VPN),
                "Имитация фейкового TLS-пакета: ВКЛ" to enableFakePacket,
                "Спуфинг регистра SNI домена: ВКЛ" to enableCaseSpoof,
                "Оптимальный фрагмент (40-100 байт)" to (firstPacketSize in 15..150)
            )

            steps.forEach { (text, checked) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (checked) Color(0xFF34D399) else Color(0xFFF87171),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = text,
                        color = if (checked) Color(0xFFC4C4C4) else Color(0xFF938F99),
                        fontSize = 11.sp,
                        fontWeight = if (checked) FontWeight.Normal else FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onApplyPreset,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFullyConfigured) Color(0xFF1B4D3E) else Color(0xFFF87171),
                    contentColor = if (isFullyConfigured) Color(0xFF34D399) else Color(0xFFFFFFFF)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(
                    text = if (isFullyConfigured) "✓ НАСТРОЕНО ОПТИМАЛЬНО" else "НАСТРОИТЬ В 1 КЛИК",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
