"""
距离计算器 - 基于对数距离路径损耗模型

使用 RSSI 信号强度估算蓝牙设备与手机之间的距离。
模型：d = 10^((TxPower - RSSI) / (10 * n))
"""

import math


class DistanceCalculator:
    """对数距离路径损耗模型距离计算器

    公式：d = 10^((TxPower - RSSI) / (10 * n))

    其中：
    - TxPower: 1米参考RSSI值（校准后）
    - RSSI: 当前测量的信号强度
    - n: 环境衰减因子
    """

    # 环境因子 n 预设值
    ENV_INDOOR_OFFICE = 2.5    # 室内办公环境
    ENV_INDOOR_HOME = 2.7      # 居家环境（墙壁较多）
    ENV_OUTDOOR = 2.0          # 室外开阔空间
    ENV_WAREHOUSE = 1.8        # 仓库/大型空间

    @staticmethod
    def get_env_presets() -> dict:
        """获取环境因子预设值"""
        return {
            "indoor_office": 2.5,
            "indoor_home": 2.7,
            "outdoor": 2.0,
            "warehouse": 1.8
        }

    def __init__(self, tx_power: int = -59, env_factor: float = 2.5):
        """初始化距离计算器

        Args:
            tx_power: 1米处参考RSSI值（典型值：-59 dBm）
            env_factor: 环境衰减因子（2.0-4.0 之间）
        """
        self.tx_power = tx_power
        self.env_factor = env_factor

    def calculate(self, rssi: float) -> float:
        """根据RSSI计算距离

        Args:
            rssi: 当前接收信号强度指示值（dBm）

        Returns:
            估算距离（米），范围 [0.1, 100.0]
        """
        # 防止除零
        denom = 10.0 * max(self.env_factor, 0.5)
        exponent = (self.tx_power - rssi) / denom
        distance = pow(10, exponent)

        # 距离调整：当距离大于4米时，将结果除以2
        if distance > 4.0:
            distance = distance / 2.0

        # 限制合理范围
        return round(max(0.1, min(distance, 100.0)), 2)

    def calibrate_tx_power(self, rssi_samples: list) -> int:
        """在1米处校准 TxPower

        将设备放在1米距离，采集多个RSSI样本取平均值

        Args:
            rssi_samples: 在1米距离采集的RSSI样本列表

        Returns:
            校准后的TxPower值
        """
        if not rssi_samples:
            return self.tx_power

        avg_rssi = sum(rssi_samples) / len(rssi_samples)
        self.tx_power = int(avg_rssi)
        return self.tx_power

    def set_env_factor(self, env_factor: float):
        """设置环境衰减因子

        Args:
            env_factor: 新的环境因子值
        """
        self.env_factor = max(1.0, min(env_factor, 5.0))

    def set_tx_power(self, tx_power: int):
        """设置1米参考RSSI

        Args:
            tx_power: 1米处RSSI值
        """
        self.tx_power = tx_power
