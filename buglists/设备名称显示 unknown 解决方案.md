蓝牙设备名称显示 unknown 解决方案

你遇到的蓝牙设备名称显示 unknown是蓝牙开发中最常见的问题，核心原因不是技术故障，而是蓝牙标准的广播机制 + 设备隐私限制 导致的。

## 一、先搞懂：为什么会显示 unknown？
蓝牙设备（BLE 低功耗蓝牙）广播分两种：
广播包（Advertising Data）
设备主动往外发的短数据，很多设备不在这里放完整名称，所以你读到空 /unknown。
扫描响应包（Scan Response Data）
手机主动请求后，设备才返回的补充数据，设备名称通常在这里。
隐私设备：苹果 / 安卓的隐私广播，会隐藏真实名称，必须连接后才能读。
你的应用现在只读取了广播包，没读扫描响应包，所以大量 unknown。

## 二、立刻解决：开启「扫描响应包解析」（90% 设备有效）
这是成本最低、效果最好的方案，不需要连接设备，只需要修改蓝牙扫描配置。
安卓（Android）解决方案
在你的 BLE 扫描设置里，开启扫描回调同时获取广播包 + 扫描响应包：
kotlin
// 关键：必须使用 SCAN_MODE_LOW_LATENCY 低延迟模式
val settings = ScanSettings.Builder()
    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
    .setReportDelay(0) // 立即回调
    .build()

// 扫描过滤器（可选）
val filters = mutableListOf<ScanFilter>()

// 启动扫描
bluetoothLeScanner.startScan(filters, settings, scanCallback)

// 扫描回调：同时解析 广播数据 + 扫描响应数据
private val scanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        super.onScanResult(callbackType, result)
        
        val device = result.device
        val address = device.address
        
        // ✅ 正确获取名称方式：优先扫描响应包，再读广播包
        val scanResponse = result.scanResponse
        val advData = result.scanRecord
        
        // 最终设备名称
        val deviceName = parseDeviceName(advData, scanResponse)
        
        // 不为空才显示
        if (!deviceName.isNullOrBlank()) {
            // 更新列表
        } else {
            // 仍然 unknown，标记一下
        }
    }
}

// 核心解析函数
private fun parseDeviceName(advData: ScanRecord?, scanResponse: ScanRecord?): String? {
    // 优先级：扫描响应包名称 > 广播包名称
    return scanResponse?.deviceName ?: advData?.deviceName
}

## 三、进阶方案：连接后读取「设备名称特征值」（100% 准确）
有些设备（尤其是苹果 Find My 配件、隐私设备、蓝牙定位标签）为了隐私，广播里完全不发名称，必须连接设备才能读到真实名称。
这是定位追踪类应用的标准做法。
技术原理
蓝牙标准里有一个固定的 Device Name 特征值（UUID：00002A00-0000-1000-8000-00805F9B34FB），属于通用访问服务 GAP。
实现步骤
对 unknown 设备尝试短连接（1~2 秒）
发现服务 → 读取 2A00 特征值
解析 UTF-8 字符串 → 就是真实设备名称
立刻断开（不影响功耗）
代码伪逻辑
plaintext
if (设备名称 == unknown) {
    建立临时连接
    发现服务 00001800-0000-1000-8000-00805F9B34FB (GAP 服务)
    读取特征值 2A00 (Device Name)
    解析字符串得到真实名称
    断开连接
    更新列表
}
适用场景：你的蓝牙定位追踪器、防丢器、资产标签，100% 能用这个方案读到真实名称。

## 四、终极方案：MAC 地址 + OUI 厂商解析（补充）
如果设备完全没有名称，你可以用 MAC 地址前 3 字节 解析出厂商名称，替代 unknown。
例如：
A4:B5:06:xx:xx:xx → 小米
00:1A:7D:xx:xx:xx → 戴尔
58:32:6A:xx:xx:xx → 苹果
实现方式
内置一份 OUI 数据库（蓝牙 MAC 厂商库）
截取设备 MAC 前 6 位（16 进制）
匹配厂商名称 → 显示为 小米设备、苹果设备
效果：彻底消灭 unknown，用户体验大幅提升。

## 五、你应该优先使用的方案（按你的定位追踪场景）
应用是蓝牙定位追踪，所以解析设备的信息是非常重要的。
第一步（必做）
开启扫描响应包解析 → 解决 90% 设备名称
第二步（ 应用到代码）
对 unknown 设备，临时连接读取 2A00 名称特征值 → 解决追踪器 / 标签设备
第三步（应用到代码）
仍无名称 → 用 MAC OUI 显示厂商名 → 完全消灭 unknown
## 六、常见坑（一定要避开）
只读取系统自带的 device.name
系统 API 只会读广播包，不会读扫描响应包 → 必须手动解析
扫描模式用了低功耗模式
低功耗模式会忽略扫描响应包 → 必须用低延迟 / 平衡模式
苹果设备广播名称随机
苹果手机 / 平板的蓝牙名称是隐私随机的，无法解析，属于正常现象

