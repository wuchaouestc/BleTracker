import android.bluetooth.BluetoothAdapter
fun main() { BluetoothAdapter::class.java.methods.filter { it.name.contains("DirectionFinding", ignoreCase = true) || it.name.contains("AoA", ignoreCase = true) || it.name.contains("5", ignoreCase = true) }.forEach { println(it.name) } }
