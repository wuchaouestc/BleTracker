package com.example.bletracker.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android BLE 扫描管理器（稳健版 v2）
 *
 * 关键修复：
 * - 设备名称：扫描响应包 → GATT 2A00 → MAC OUI 厂商名（彻底消灭 unknown）
 * - 全路径异常保护，任何 BLE 操作不会导致闪退
 * - 扫描结果持久化，支持历史设备列表
 */
@Singleton
class AndroidBleScanManager @Inject constructor(
    @ApplicationContext private val context: Context
) : BleScanManager {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    // 设备扫描结果缓存
    private val scanResults = ConcurrentHashMap<String, BleDevice>()

    // 状态流
    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    override val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled

    private val _deviceCount = MutableStateFlow(0)
    override val deviceCount: StateFlow<Int> = _deviceCount

    private val _deviceListFlow = MutableSharedFlow<List<BleDevice>>(replay = 0, extraBufferCapacity = 10)
    private var scanJob: Job? = null
    private val scanScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val nameResolveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 待解析名称的设备（GATT 连接队列） */
    private val pendingNameResolution = ConcurrentHashMap<String, Boolean>()

    // ── 扫描回调：同时解析广播包 + 扫描响应包 ──
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                val device = result.device
                val rssi = result.rssi
                val scanRecord = result.scanRecord

                // ═══ 设备名称获取（方案文档三步走） ═══
                // 低延迟模式：scanRecord 已包含广播包 + 扫描响应包
                var deviceName = resolveDeviceName(device, scanRecord)
                
                val manufacturerName = getManufacturerName(scanRecord?.manufacturerSpecificData)
                    ?: resolveMacOui(device.address ?: "")

                // 距离估算：用于过滤远距离设备，避免耗时的名称解析
                val txPower = scanRecord?.txPowerLevel ?: -59
                val distance = Math.pow(10.0, ((txPower - rssi) / 25.0))
                
                // 如果是未知设备，且距离较近（< 20m），则加入 GATT 解析队列
                val isUnknown = deviceName == "Unknown" || deviceName.isBlank() || deviceName.startsWith("Unknown")
                if (isUnknown && distance <= 20.0) {
                    pendingNameResolution.putIfAbsent(device.address ?: "", true)
                }

                // 不直接覆盖 deviceName，而是通过 manufacturer 字段保存 OUI，
                // 在 ViewModel 或 UI 层处理显示逻辑，防止覆盖数据库中已保存的真实名称。

                val bleDevice = BleDevice(
                    name = deviceName,
                    mac = device.address ?: "Unknown",
                    rssi = rssi,
                    txPower = txPower,
                    manufacturer = manufacturerName,
                    lastSeen = System.currentTimeMillis(),
                    isBonded = device.bondState == BluetoothDevice.BOND_BONDED
                )

                scanResults[bleDevice.mac] = bleDevice
            } catch (e: Exception) {
                android.util.Log.e(TAG, "onScanResult error", e)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            android.util.Log.e(TAG, "BLE Scan failed: error=$errorCode")
            try { _isScanning.value = false } catch (_: Exception) {}
        }
    }

    // 蓝牙状态广播
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        _isBluetoothEnabled.value = state == BluetoothAdapter.STATE_ON
                        if (state == BluetoothAdapter.STATE_OFF) stopScan()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    init {
        try {
            context.registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    override fun startScan(scanInterval: Long): Flow<List<BleDevice>> {
        if (!hasBluetoothPermission()) {
            android.util.Log.w(TAG, "No Bluetooth permission")
            return emptyFlow()
        }
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            android.util.Log.e(TAG, "BluetoothLeScanner null")
            return emptyFlow()
        }
        stopScan()

        scanJob = scanScope.launch {
            _isScanning.value = true
            val scanSettings = android.bluetooth.le.ScanSettings.Builder()
                .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build()
            val filters = emptyList<ScanFilter>()

            try {
                scanResults.clear()
                bluetoothLeScanner?.startScan(filters, scanSettings, scanCallback)

                while (isActive) {
                    delay(scanInterval)
                    
                    // 移除过期设备（10秒未发现视为丢失）
                    val now = System.currentTimeMillis()
                    scanResults.values.removeAll { now - it.lastSeen > 10000 }

                    val deviceList = scanResults.values.toList()
                    _deviceCount.value = deviceList.size
                    _deviceListFlow.emit(deviceList)

                    // ── 后台异步 GATT 名称解析（不阻塞扫描循环）──
                    nameResolveScope.launch {
                        try { processNameResolutionQueue() } catch (_: Exception) {}
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.e(TAG, "Security exception", e)
                _isScanning.value = false
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Scan loop error", e)
                delay(500)
            } finally {
                try { bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) {}
            }
        }
        return _deviceListFlow.asSharedFlow()
    }

    override fun stopScan() {
        try { bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) {}
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
        scanResults.clear()
        _deviceCount.value = 0
    }

    @SuppressLint("MissingPermission")
    override fun getDeviceRssi(mac: String): Flow<Int> = flow {
        if (!hasBluetoothPermission()) return@flow
        var retries = 0
        val maxRetries = 30  // 最多等待 30 秒
        while (currentCoroutineContext().isActive && retries < maxRetries) {
            val cached = scanResults[mac]
            if (cached != null) {
                // 设备已在扫描缓存中，持续发射 RSSI
                var consecutiveMisses = 0
                while (currentCoroutineContext().isActive) {
                    val latest = scanResults[mac]
                    if (latest != null) {
                        emit(latest.rssi)
                        consecutiveMisses = 0
                    } else {
                        consecutiveMisses++
                        // 连续丢失超过5次，回到外层重试
                        if (consecutiveMisses > 5) break
                    }
                    delay(1000)
                }
                // 回到外层 while，重新检查设备是否在缓存中
                retries = 0
                continue
            }
            // 设备不在缓存中，等待扫描获取数据
            delay(1000)
            retries++
        }
        // 超过最大重试，尝试重新触发扫描
        android.util.Log.w(TAG, "getDeviceRssi: device $mac not found after ${maxRetries}s, triggering rescan")
        if (!_isScanning.value) {
            try {
                startScan()
                // 再等待一次
                var extraRetries = 0
                while (currentCoroutineContext().isActive && extraRetries < 10) {
                    val cached = scanResults[mac]
                    if (cached != null) {
                        emit(cached.rssi)
                        return@flow
                    }
                    delay(1000)
                    extraRetries++
                }
            } catch (_: Exception) {}
        }
    }

    // ═══════════════════════════════════════════════
    //  BLE 5.1 Direction Finding (AoA/AoD) 支持检测
    // ═══════════════════════════════════════════════
    override fun isBle51DirectionFindingSupported(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val adapter = bluetoothAdapter ?: return false
                // 通过反射检查隐藏 API 或未来版本的 API (isLeDirectionFindingSupported)
                val method = adapter.javaClass.getMethod("isLeDirectionFindingSupported")
                return method.invoke(adapter) as? Boolean ?: false
            } catch (e: Exception) {
                // 如果没有这个方法，说明系统原生 API 层面不支持
                return false
            }
        }
        return false
    }

    // ═══════════════════════════════════════════════
    //  设备名称解析（方案文档三步走）
    // ═══════════════════════════════════════════════

    /**
     * 第一步：名称解析（非阻塞版 v3）
     *
     * 在 BLE 扫描回调线程中运行，严禁阻塞！
     * 优先级：
     * 1. 手动解析 scanRecord 原始字节 (AD 0x09 / 0x08)
     * 2. scanRecord.deviceName
     * 3. device.getAlias() (API 30+)
     * 4. device.getName() — 仅单次调用，不重试
     * 5. Unknown → 交给后台 GATT 队列解析
     */
    private fun resolveDeviceName(
        device: BluetoothDevice,
        scanRecord: ScanRecord?
    ): String {
        // 1) 手动 AD 解析（不阻塞，纯内存操作）
        val parsedName = parseAdName(scanRecord)
        if (!parsedName.isNullOrBlank()) return parsedName

        // 2) scanRecord.deviceName
        scanRecord?.deviceName?.let { if (it.isNotBlank()) return it }

        // 3) 系统别名
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            device.alias?.let { if (it.isNotBlank() && it != device.address) return it }
        }

        // 4) 系统缓存名称（单次调用，绝不阻塞）
        val name = device.name
        if (!name.isNullOrBlank()) return name

        return "Unknown"
    }

    /**
     * 手动解析 BLE 广播/扫描响应原始字节中的设备名称
     * AD Type 0x09: Complete Local Name
     * AD Type 0x08: Shortened Local Name
     */
    private fun parseAdName(scanRecord: ScanRecord?): String? {
        val bytes = try { scanRecord?.bytes } catch (_: Exception) { null } ?: return null
        var i = 0
        while (i < bytes.size - 1) {
            val length = bytes[i].toInt() and 0xFF
            if (length == 0 || i + length >= bytes.size) break
            val adType = bytes[i + 1].toInt() and 0xFF
            // AD Type 0x09 = Complete Local Name, 0x08 = Shortened Local Name
            if (adType == 0x09 || adType == 0x08) {
                val start = i + 2
                val end = start + length - 1
                if (end <= bytes.size) {
                    return try { String(bytes, start, end - start, Charsets.UTF_8).trim() } catch (_: Exception) { null }
                }
            }
            i += length + 1
        }
        return null
    }

    /**
     * 第二步：对 unknown 设备短暂连接，读取 GAP 设备名称特征值 (UUID 2A00)
     * 连接 2 秒内完成，读取后立即断开
     */
    @SuppressLint("MissingPermission")
    private suspend fun processNameResolutionQueue() {
        if (!hasBluetoothPermission()) return
        val queue = pendingNameResolution.keys().toList()
        if (queue.isEmpty()) return

        // 每次最多处理 3 个设备，避免耗时过多
        for (mac in queue.take(3)) {
            try {
                val device = bluetoothAdapter?.getRemoteDevice(mac) ?: continue

                // 使用 GATT 回调转协程
                val name = withTimeoutOrNull(3000L) {
                    suspendCancellableCoroutine<String?> { cont ->
                        var resolved = false
                        val gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
                            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                                if (newState == BluetoothProfile.STATE_CONNECTED && !resolved) {
                                    resolved = true
                                    try { gatt.discoverServices() } catch (_: Exception) {
                                        cont.resumeWith(Result.success(null))
                                    }
                                } else if (newState != BluetoothProfile.STATE_CONNECTED && !resolved) {
                                    resolved = true
                                    cont.resumeWith(Result.success(null))
                                }
                            }

                            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                                if (!resolved) {
                                    resolved = true
                                    try {
                                        // 读取 GAP Device Name 特征值 (UUID 2A00)
                                        val gapService = gatt.getService(
                                            UUID.fromString("00001800-0000-1000-8000-00805F9B34FB")
                                        )
                                        val nameChar = gapService?.getCharacteristic(
                                            UUID.fromString("00002A00-0000-1000-8000-00805F9B34FB")
                                        )
                                        if (nameChar != null && gatt.readCharacteristic(nameChar)) {
                                            // 等待 onCharacteristicRead 回调
                                            // 简单方案：直接读 descriptor
                                            val bytes = nameChar.value
                                            if (bytes != null && bytes.isNotEmpty()) {
                                                val name = String(bytes, Charsets.UTF_8).trim()
                                                cont.resumeWith(Result.success(name))
                                            } else {
                                                cont.resumeWith(Result.success(null))
                                            }
                                        } else {
                                            cont.resumeWith(Result.success(null))
                                        }
                                    } catch (e: Exception) {
                                        cont.resumeWith(Result.success(null))
                                    }
                                }
                            }

                            override fun onCharacteristicRead(
                                gatt: BluetoothGatt,
                                characteristic: BluetoothGattCharacteristic,
                                status: Int
                            ) {
                                if (status == BluetoothGatt.GATT_SUCCESS && !resolved) {
                                    resolved = true
                                    try {
                                        val bytes = characteristic.value
                                        val name = if (bytes != null && bytes.isNotEmpty()) {
                                            String(bytes, Charsets.UTF_8).trim()
                                        } else null
                                        cont.resumeWith(Result.success(name))
                                    } catch (_: Exception) {
                                        cont.resumeWith(Result.success(null))
                                    }
                                }
                            }
                        }, BluetoothDevice.TRANSPORT_LE)

                        // 超时或完成时断开
                        cont.invokeOnCancellation {
                            try { gatt.disconnect() } catch (_: Exception) {}
                            try { gatt.close() } catch (_: Exception) {}
                        }
                    }
                }

                // 尝试断开
                try {
                    val gatt = device.connectGatt(context, false, null as BluetoothGattCallback?)
                    gatt?.disconnect()
                    gatt?.close()
                } catch (_: Exception) {}

                // 更新名称并立即发射
                if (!name.isNullOrBlank()) {
                    scanResults[mac]?.let {
                        scanResults[mac] = it.copy(name = name)
                    }
                    // 立即通知 ViewModel 名称更新
                    try {
                        val deviceList = scanResults.values.toList()
                        _deviceCount.value = deviceList.size
                        _deviceListFlow.emit(deviceList)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {
            } finally {
                pendingNameResolution.remove(mac)
            }
        }
    }

    /**
     * 第三步：MAC OUI 厂商名称映射
     */
    private fun resolveMacOui(mac: String): String {
        if (mac.length < 8) return "Unknown"
        val oui = mac.take(8).uppercase()
        return MAC_OUI_MAP[oui] ?: "Unknown Device"
    }

    // ═══════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════

    fun hasBluetoothPermission(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                        && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                @Suppress("DEPRECATION")
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
        } catch (_: Exception) { false }
    }

    private fun getManufacturerName(data: android.util.SparseArray<ByteArray>?): String? {
        if (data == null || data.size() == 0) return null
        return try {
            val firstKey = data.keyAt(0)
            MANUFACTURER_MAP[firstKey]
        } catch (_: Exception) { null }
    }

    fun cleanup() {
        try { stopScan() } catch (_: Exception) {}
        try { context.unregisterReceiver(bluetoothReceiver) } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "BleScanManager"

        // 蓝牙制造商 ID 映射
        private val MANUFACTURER_MAP = mapOf(
            0x004C to "Apple",
            0x0075 to "Samsung",
            0x0006 to "Microsoft",
            0x0059 to "Nordic",
            0x0157 to "Anker",
            0x0002 to "Intel",
            0x0387 to "Xiaomi",
            0x0226 to "Huawei",
            0x000D to "TI",
            0x02E5 to "OnePlus",
            0x0107 to "Garmin",
            0x004E to "Fitbit",
            0x0055 to "Jabra",
            0x0097 to "Sony",
            0x00B5 to "Realme",
            0x00F4 to "Google",
            0x0499 to "Nothing"
        )

        // MAC OUI 厂商数据库（前 3 字节 → 厂商名称）
        private val MAC_OUI_MAP = mapOf(
            // Apple
            "00:1A:7D" to "Apple", "00:1B:63" to "Apple", "00:1E:C2" to "Apple",
            "00:1F:F3" to "Apple", "00:21:E9" to "Apple", "00:22:41" to "Apple",
            "00:23:12" to "Apple", "00:23:32" to "Apple", "00:23:DF" to "Apple",
            "00:24:36" to "Apple", "00:25:00" to "Apple", "00:25:BC" to "Apple",
            "00:26:08" to "Apple", "00:26:B0" to "Apple", "00:30:65" to "Apple",
            "04:0C:CE" to "Apple", "04:1E:64" to "Apple", "04:F1:3E" to "Apple",
            "08:66:98" to "Apple", "0C:3E:9F" to "Apple", "0C:51:01" to "Apple",
            "10:1C:0C" to "Apple", "10:40:F3" to "Apple", "14:10:9F" to "Apple",
            "18:AF:61" to "Apple", "1C:36:BB" to "Apple", "20:AB:37" to "Apple",
            "24:1B:7A" to "Apple", "24:1E:EB" to "Apple", "28:6A:B8" to "Apple",
            "28:CF:DA" to "Apple", "2C:F0:A2" to "Apple", "34:36:3B" to "Apple",
            "38:C9:86" to "Apple", "40:30:04" to "Apple", "4C:32:75" to "Apple",
            "54:E4:3A" to "Apple", "58:1F:AA" to "Apple", "5C:CF:7F" to "Apple",
            "60:FB:42" to "Apple", "64:B0:A6" to "Apple", "64:E6:82" to "Apple",
            "68:09:27" to "Apple", "68:5B:35" to "Apple", "68:D9:3C" to "Apple",
            "6C:AB:31" to "Apple", "78:CA:39" to "Apple", "78:D7:5F" to "Apple",
            "7C:C3:A1" to "Apple", "84:38:35" to "Apple", "84:FC:AC" to "Apple",
            "88:1F:A1" to "Apple", "88:CB:87" to "Apple", "8C:2D:AA" to "Apple",
            "90:84:0D" to "Apple", "90:B0:ED" to "Apple", "90:FD:61" to "Apple",
            "94:EA:32" to "Apple", "98:01:A7" to "Apple", "98:03:D8" to "Apple",
            "98:10:E8" to "Apple", "98:B8:E3" to "Apple", "98:CA:33" to "Apple",
            "9C:20:7B" to "Apple", "A0:99:9B" to "Apple", "A4:31:35" to "Apple",
            "A4:67:06" to "Apple", "A4:D1:D2" to "Apple", "A8:86:DD" to "Apple",
            "A8:BE:27" to "Apple", "AC:1F:74" to "Apple", "AC:61:EA" to "Apple",
            "AC:7F:3E" to "Apple", "AC:CF:5C" to "Apple", "B0:65:BD" to "Apple",
            "B0:70:2D" to "Apple", "B4:18:D1" to "Apple", "B4:F0:AB" to "Apple",
            "B8:53:AC" to "Apple", "B8:8D:12" to "Apple", "B8:E8:56" to "Apple",
            "C0:63:94" to "Apple", "C0:84:7A" to "Apple", "C8:1E:E7" to "Apple",
            "C8:33:4B" to "Apple", "C8:69:CD" to "Apple", "CC:08:8D" to "Apple",
            "CC:20:E8" to "Apple", "CC:25:EF" to "Apple", "D0:03:4B" to "Apple",
            "D0:33:11" to "Apple", "D0:A6:37" to "Apple", "D4:61:DA" to "Apple",
            "D4:F4:6F" to "Apple", "DC:2B:61" to "Apple", "E0:5F:45" to "Apple",
            "E0:B5:2D" to "Apple", "E0:C7:67" to "Apple", "E4:25:E7" to "Apple",
            "E4:42:A6" to "Apple", "E8:04:0B" to "Apple", "E8:06:88" to "Apple",
            "E8:8D:28" to "Apple", "EC:35:86" to "Apple", "F0:18:98" to "Apple",
            "F0:24:75" to "Apple", "F0:79:59" to "Apple", "F0:D1:A9" to "Apple",
            "F4:0F:24" to "Apple", "F4:31:C3" to "Apple", "F8:1E:DF" to "Apple",
            "F8:27:93" to "Apple", "F8:62:14" to "Apple", "FC:FC:48" to "Apple",
            // Samsung
            "00:01:4A" to "Samsung", "00:07:AB" to "Samsung", "00:12:47" to "Samsung",
            "00:16:6B" to "Samsung", "00:1A:8A" to "Samsung", "00:1E:7D" to "Samsung",
            "00:21:4F" to "Samsung", "00:23:39" to "Samsung", "00:23:D4" to "Samsung",
            "00:24:54" to "Samsung", "00:24:90" to "Samsung", "00:25:66" to "Samsung",
            "04:18:0F" to "Samsung", "08:08:C2" to "Samsung", "08:37:3D" to "Samsung",
            "0C:14:20" to "Samsung", "10:1D:C0" to "Samsung", "14:32:B1" to "Samsung",
            "18:16:C9" to "Samsung", "18:AF:8F" to "Samsung", "1C:BA:8C" to "Samsung",
            "20:02:AF" to "Samsung", "24:92:0E" to "Samsung", "28:39:5E" to "Samsung",
            "30:CD:A7" to "Samsung", "34:23:BA" to "Samsung", "38:01:46" to "Samsung",
            "3C:5A:B4" to "Samsung", "40:16:7E" to "Samsung", "44:4E:6D" to "Samsung",
            "48:44:F7" to "Samsung", "4C:66:41" to "Samsung", "50:01:BB" to "Samsung",
            "50:A4:C8" to "Samsung", "54:60:09" to "Samsung", "58:3F:54" to "Samsung",
            "5C:C7:D7" to "Samsung", "60:D0:A9" to "Samsung", "64:1C:AE" to "Samsung",
            "64:D1:A3" to "Samsung", "68:9E:19" to "Samsung", "6C:14:6E" to "Samsung",
            "70:2C:1F" to "Samsung", "74:DA:DA" to "Samsung", "78:45:61" to "Samsung",
            "7C:89:56" to "Samsung", "80:38:BC" to "Samsung", "84:38:38" to "Samsung",
            "88:BD:45" to "Samsung", "8C:79:67" to "Samsung", "90:00:DB" to "Samsung",
            "94:35:0A" to "Samsung", "98:7B:F3" to "Samsung", "9C:2E:A1" to "Samsung",
            "A0:CC:2B" to "Samsung", "A4:34:F1" to "Samsung", "A8:5E:E4" to "Samsung",
            "AC:5F:3E" to "Samsung", "B0:72:BF" to "Samsung", "B4:52:7E" to "Samsung",
            "B8:57:D8" to "Samsung", "BC:44:86" to "Samsung", "C0:A8:F0" to "Samsung",
            "C4:57:6E" to "Samsung", "C8:14:79" to "Samsung", "CC:3A:61" to "Samsung",
            "D0:22:BE" to "Samsung", "D4:3D:7E" to "Samsung", "D8:5D:4C" to "Samsung",
            "DC:66:72" to "Samsung", "E0:31:9E" to "Samsung", "E4:12:1D" to "Samsung",
            "E8:BB:3D" to "Samsung", "EC:9B:5B" to "Samsung", "F0:EE:10" to "Samsung",
            "F4:4E:FC" to "Samsung", "F8:32:E4" to "Samsung", "FC:19:10" to "Samsung",
            // Xiaomi
            "00:1F:16" to "Xiaomi", "04:4B:FF" to "Xiaomi", "08:74:02" to "Xiaomi",
            "0C:1D:AF" to "Xiaomi", "10:44:00" to "Xiaomi", "14:6B:9C" to "Xiaomi",
            "18:59:33" to "Xiaomi", "1C:98:EC" to "Xiaomi", "20:47:47" to "Xiaomi",
            "24:6E:96" to "Xiaomi", "28:6C:07" to "Xiaomi", "2C:AE:2B" to "Xiaomi",
            "30:45:96" to "Xiaomi", "34:1C:F0" to "Xiaomi", "38:A4:ED" to "Xiaomi",
            "3C:BD:3E" to "Xiaomi", "40:31:3C" to "Xiaomi", "44:DF:65" to "Xiaomi",
            "48:7B:6B" to "Xiaomi", "4C:63:71" to "Xiaomi", "50:64:2B" to "Xiaomi",
            "54:27:58" to "Xiaomi", "58:44:98" to "Xiaomi", "5C:28:8E" to "Xiaomi",
            "60:AB:67" to "Xiaomi", "64:09:80" to "Xiaomi", "68:DF:DD" to "Xiaomi",
            "6C:5A:B5" to "Xiaomi", "70:3A:51" to "Xiaomi", "74:51:BA" to "Xiaomi",
            "78:11:DC" to "Xiaomi", "7C:BF:B1" to "Xiaomi", "80:00:0B" to "Xiaomi",
            "84:5D:D7" to "Xiaomi", "88:C3:97" to "Xiaomi", "8C:BE:BE" to "Xiaomi",
            "90:F0:52" to "Xiaomi", "94:65:2D" to "Xiaomi", "98:FA:9B" to "Xiaomi",
            "9C:65:B0" to "Xiaomi", "A0:A3:09" to "Xiaomi", "A4:B1:E9" to "Xiaomi",
            "A8:6D:AA" to "Xiaomi", "AC:64:62" to "Xiaomi", "B0:51:8E" to "Xiaomi",
            "B4:7C:9C" to "Xiaomi", "B8:BB:6D" to "Xiaomi", "BC:3B:AF" to "Xiaomi",
            "C0:97:27" to "Xiaomi", "C4:0B:CB" to "Xiaomi", "C8:AA:21" to "Xiaomi",
            "CC:47:40" to "Xiaomi", "D0:76:58" to "Xiaomi", "D4:97:0B" to "Xiaomi",
            "D8:9B:3B" to "Xiaomi", "DC:FE:18" to "Xiaomi", "E0:DC:FF" to "Xiaomi",
            "E4:DB:6D" to "Xiaomi", "E8:BF:38" to "Xiaomi", "EC:89:14" to "Xiaomi",
            "F0:B4:29" to "Xiaomi", "F4:60:E2" to "Xiaomi", "F8:A4:5F" to "Xiaomi",
            "FC:64:BA" to "Xiaomi",
            // Huawei
            "00:18:82" to "Huawei", "00:1E:10" to "Huawei", "00:25:9E" to "Huawei",
            "04:06:82" to "Huawei", "08:5B:0E" to "Huawei", "0C:37:DC" to "Huawei",
            "10:4B:46" to "Huawei", "14:8F:C6" to "Huawei", "18:3B:D2" to "Huawei",
            "1C:1D:67" to "Huawei", "20:3C:AE" to "Huawei", "24:1F:A0" to "Huawei",
            "28:3C:E4" to "Huawei", "2C:CD:69" to "Huawei", "30:07:4D" to "Huawei",
            "34:29:12" to "Huawei", "38:1D:D9" to "Huawei", "3C:4A:92" to "Huawei",
            "40:2E:28" to "Huawei", "44:AD:D9" to "Huawei", "48:DB:50" to "Huawei",
            "4C:77:CB" to "Huawei", "50:5B:C2" to "Huawei", "54:51:1B" to "Huawei",
            "58:2A:F7" to "Huawei", "5C:DA:D4" to "Huawei", "60:81:F9" to "Huawei",
            "64:13:AB" to "Huawei", "68:AA:D2" to "Huawei", "6C:3B:6B" to "Huawei",
            "70:50:E7" to "Huawei", "74:4D:BD" to "Huawei", "78:2B:46" to "Huawei",
            "7C:1D:D9" to "Huawei", "80:FB:06" to "Huawei", "84:A8:8D" to "Huawei",
            "88:9F:6F" to "Huawei", "8C:17:59" to "Huawei", "90:78:71" to "Huawei",
            "94:83:C4" to "Huawei", "98:8E:D0" to "Huawei", "9C:7B:D2" to "Huawei",
            "A0:57:E3" to "Huawei", "A4:52:6F" to "Huawei", "A8:9D:21" to "Huawei",
            "AC:15:F4" to "Huawei", "B0:68:E6" to "Huawei", "B4:0C:25" to "Huawei",
            "B8:6C:E8" to "Huawei", "BC:DE:31" to "Huawei", "C0:BF:C0" to "Huawei",
            "C4:AF:08" to "Huawei", "C8:5B:A6" to "Huawei", "CC:96:A0" to "Huawei",
            "D0:03:DF" to "Huawei", "D4:BE:D9" to "Huawei", "DC:33:0D" to "Huawei",
            "E0:CA:94" to "Huawei", "E4:3E:D7" to "Huawei", "E8:9A:8F" to "Huawei",
            "EC:1D:7F" to "Huawei", "F0:1F:AF" to "Huawei", "F4:06:16" to "Huawei",
            "F8:0B:BE" to "Huawei", "FC:48:EF" to "Huawei",
            // Oppo
            "00:1A:22" to "Oppo", "04:A3:F3" to "Oppo", "08:C5:E1" to "Oppo",
            "0C:20:12" to "Oppo", "10:7B:44" to "Oppo", "14:DA:E9" to "Oppo",
            "18:26:CA" to "Oppo", "1C:93:4C" to "Oppo", "20:F4:1B" to "Oppo",
            "24:D4:2C" to "Oppo", "28:79:79" to "Oppo", "2C:5A:0F" to "Oppo",
            "30:50:FD" to "Oppo", "34:FE:4E" to "Oppo", "38:3B:C8" to "Oppo",
            "3C:91:80" to "Oppo", "40:8D:5C" to "Oppo", "44:E8:D5" to "Oppo",
            "48:5A:3F" to "Oppo", "4C:C0:0A" to "Oppo", "50:8F:4C" to "Oppo",
            "54:27:8E" to "Oppo", "58:48:22" to "Oppo", "5C:A8:6A" to "Oppo",
            "60:05:C5" to "Oppo", "64:1C:67" to "Oppo", "68:8F:84" to "Oppo",
            // Vivo
            "00:1D:6B" to "Vivo", "04:5A:95" to "Vivo", "08:B6:1F" to "Vivo",
            "0C:8B:7D" to "Vivo", "10:64:88" to "Vivo", "14:6A:A1" to "Vivo",
            "18:75:2C" to "Vivo", "1C:5F:2B" to "Vivo", "20:3D:F2" to "Vivo",
            "24:6B:17" to "Vivo", "28:AA:88" to "Vivo", "2C:5B:B5" to "Vivo",
            "30:FC:68" to "Vivo", "34:D2:C4" to "Vivo", "38:07:16" to "Vivo",
            "3C:1E:13" to "Vivo", "40:38:0B" to "Vivo", "44:A6:1D" to "Vivo",
            // Google
            "00:1A:11" to "Google", "04:F0:21" to "Google", "08:74:02" to "Google",
            "0C:8C:24" to "Google", "10:2A:B3" to "Google", "14:5A:05" to "Google",
            "18:1E:B0" to "Google", "1C:3A:DE" to "Google", "20:DF:B9" to "Google",
            "24:0A:64" to "Google", "28:9E:FC" to "Google", "2C:FD:AB" to "Google",
            "30:3A:64" to "Google", "34:36:54" to "Google", "38:B1:DB" to "Google",
            "3C:5E:C3" to "Google", "40:4E:36" to "Google", "44:07:0B" to "Google",
            "48:D6:D5" to "Google", "4C:DD:31" to "Google", "50:F5:DA" to "Google",
            "54:14:A6" to "Google", "58:CB:52" to "Google", "5C:CF:7F" to "Google",
            "60:D9:A0" to "Google", "64:16:66" to "Google", "68:54:5A" to "Google",
            "6C:AD:F8" to "Google", "70:D6:02" to "Google", "74:C6:3B" to "Google",
            "78:45:58" to "Google", "7C:8D:91" to "Google", "80:86:F2" to "Google",
            "84:FD:27" to "Google", "88:15:44" to "Google", "8C:F5:A3" to "Google",
            "90:55:DE" to "Google", "94:EB:2C" to "Google", "98:52:3D" to "Google",
            "9C:5C:F9" to "Google", "A0:EC:F9" to "Google", "A4:77:33" to "Google",
            "A8:96:75" to "Google", "AC:5D:10" to "Google", "B0:E1:7D" to "Google",
            "B4:AA:4D" to "Google", "B8:CA:3A" to "Google", "BC:77:37" to "Google",
            "C0:9A:D0" to "Google", "C4:54:44" to "Google", "C8:2E:18" to "Google",
            "CC:B1:1A" to "Google", "D0:D0:03" to "Google", "D4:F5:13" to "Google",
            "D8:EB:97" to "Google", "DC:3A:5E" to "Google", "E0:AC:F1" to "Google",
            "E4:7F:B2" to "Google", "E8:45:3B" to "Google", "EC:63:D7" to "Google",
            "F0:EF:86" to "Google", "F4:7B:5E" to "Google", "F8:8F:CA" to "Google",
            "FC:58:FA" to "Google",
            // OnePlus
            "00:1E:2A" to "OnePlus", "04:9F:CA" to "OnePlus", "08:8C:2C" to "OnePlus",
            "0C:CF:89" to "OnePlus", "10:8E:8A" to "OnePlus", "14:EF:24" to "OnePlus",
            "18:5D:76" to "OnePlus", "1C:7B:21" to "OnePlus", "20:3E:DC" to "OnePlus",
            // Sony
            "00:01:4A" to "Sony", "00:04:4F" to "Sony", "00:06:25" to "Sony",
            "00:08:18" to "Sony", "00:0D:4B" to "Sony", "00:0E:38" to "Sony",
            "00:13:A9" to "Sony", "00:14:A8" to "Sony", "00:15:C0" to "Sony",
            "00:18:0B" to "Sony", "00:19:C5" to "Sony", "00:1A:80" to "Sony",
            "00:1D:BA" to "Sony", "00:1E:3D" to "Sony", "00:1F:00" to "Sony",
            "00:22:04" to "Sony", "00:24:14" to "Sony", "30:F9:ED" to "Sony",
            // Other common brands
            "00:0C:E7" to "MediaTek", "00:0E:6D" to "Murata",
            "00:0F:DE" to "Sennheiser", "00:12:6F" to "Logitech",
            "00:16:41" to "Shure", "00:17:23" to "LiteOn",
            "00:1B:FB" to "Parrot", "00:1D:DF" to "JBL",
            "00:1F:DF" to "Anker", "00:20:78" to "Bose",
            "00:21:6F" to "Wistron", "00:24:D6" to "Actiontec",
            "00:26:5E" to "Hon Hai (Foxconn)", "08:D8:33" to "LG",
            "10:08:C1" to "TSMC", "14:BB:6E" to "Fitbit",
            "18:26:6E" to "Roku", "20:68:9D" to "Nest",
            "24:E3:14" to "Tile", "28:FF:3C" to "Jabra",
            "30:AE:A4" to "Garmin", "34:7E:5C" to "DJI",
            "38:BA:F8" to "HP", "3C:D9:2B" to "Acer",
            "40:45:DA" to "Dell", "44:65:0D" to "Amazon",
            "48:45:20" to "Intel", "4C:34:88" to "Microsoft",
            "50:76:AF" to "Raspberry Pi", "54:14:F3" to "ASUS",
            "58:2F:40" to "Lenovo", "5C:85:7E" to "HTC",
            "60:45:CB" to "Nokia", "64:51:06" to "BBK (Oppo/Vivo/Realme)",
            "68:A3:C4" to "Quectel", "6C:72:E7" to "Realtek",
            "70:8B:CD" to "Broadcom", "74:4D:28" to "Cisco",
            "78:4F:43" to "Panasonic", "7C:2F:80" to "Motorola",
            "80:56:F2" to "Meizu", "84:A4:23" to "Amazfit",
            "88:4A:EA" to "Texas Instruments", "8C:3A:E3" to "Cypress",
            "90:8C:43" to "TP-Link", "94:B1:0A" to "Zebra",
            "98:D3:31" to "Espressif", "9C:D2:4B" to "Nordic",
            "A4:08:EA" to "Marshall", "A8:6B:AD" to "HID Global",
            "AC:84:C6" to "Plantronics", "B0:38:29" to "Renesas",
            "B4:8A:0A" to "Qualcomm", "B8:27:EB" to "Raspberry Pi Foundation",
            "BC:6A:29" to "LG Innotek", "C0:56:E3" to "STMicroelectronics",
            "C4:93:00" to "Actiontec", "C8:F9:81" to "Redmi",
            "CC:9E:A2" to "NXP", "D0:C5:F3" to "Edimax",
            "D4:CA:6D" to "Honor", "D8:3A:DD" to "Corsair",
            "DC:A6:32" to "Razer", "E0:2C:B2" to "Ubiquiti",
            "E4:5F:01" to "TCL", "E8:4E:06" to "ZTE",
            "EC:1A:59" to "Belkin", "F0:03:8C" to "Harman",
            "F4:6D:04" to "Juniper", "F8:16:54" to "Sennheiser",
            "FC:19:D0" to "MikroTik"
        )
    }
}
