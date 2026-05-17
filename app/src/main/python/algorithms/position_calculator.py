"""
极坐标定位计算器

将 RSSI + 距离转换为雷达图上的极坐标位置，
用于在雷达扫描界面中以可视化方式显示目标蓝牙设备相对于本设备的位置。
"""

import math
import random


class PositionCalculator:
    """极坐标定位计算器

    基于 RSSI 信号强度估算目标设备的角度和距离。
    信号越强，位置估算越稳定（角度变化越小）。
    """

    @staticmethod
    def rssi_to_polar(rssi: float, distance: float,
                      prev_angle: float = None) -> tuple:
        """将 RSSI 和距离转换为极坐标 (距离, 角度)

        RSSI越大（信号越强），置信度越高，角度变化越小。
        基于前一角度添加随机游走模拟定位不确定性。

        Args:
            rssi: RSSI 信号值（dBm，如 -50 为强信号）
            distance: 估算距离（米）
            prev_angle: 前一帧的角度（弧度），用于平滑过渡

        Returns:
            (distance, angle) 极坐标元组，角度单位为弧度
        """
        # 计算置信度：RSSI 范围约 -100 到 -30
        # 归一化到 0.3 - 0.95 之间
        confidence = max(0.3, min(0.95, (rssi + 100.0) / 60.0))

        if prev_angle is None:
            # 首次定位：随机初始角度
            angle = random.uniform(0, 2 * math.pi)
        else:
            # 基于前一角度添加随机游走
            max_delta = (1.0 - confidence) * math.pi
            angle = prev_angle + random.uniform(-max_delta, max_delta)

        # 归一化到 [0, 2π)
        angle = angle % (2.0 * math.pi)

        return (distance, angle)

    @staticmethod
    def polar_to_cartesian(distance: float, angle: float) -> tuple:
        """极坐标转笛卡尔坐标

        Args:
            distance: 距离（米）
            angle: 角度（弧度）

        Returns:
            (x, y) 笛卡尔坐标
        """
        x = distance * math.cos(angle)
        y = distance * math.sin(angle)
        return (x, y)

    @staticmethod
    def cartesian_to_polar(x: float, y: float) -> tuple:
        """笛卡尔坐标转极坐标

        Args:
            x: X坐标
            y: Y坐标

        Returns:
            (distance, angle) 极坐标
        """
        distance = math.sqrt(x * x + y * y)
        angle = math.atan2(y, x)
        if angle < 0:
            angle += 2.0 * math.pi
        return (distance, angle)

    @staticmethod
    def get_signal_level(rssi: int) -> int:
        """根据RSSI获取信号等级（1-5）

        Args:
            rssi: RSSI值

        Returns:
            信号等级: 5(强) ~ 1(弱)
        """
        if rssi >= -50:
            return 5
        elif rssi >= -60:
            return 4
        elif rssi >= -70:
            return 3
        elif rssi >= -80:
            return 2
        else:
            return 1
