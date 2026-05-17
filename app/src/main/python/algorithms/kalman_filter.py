"""
卡尔曼滤波器 - 用于平滑 RSSI 信号

一维卡尔曼滤波器实现，用于对蓝牙 RSSI 信号进行实时平滑滤波，
减少多径效应和环境噪声导致的信号波动。
"""


class KalmanFilter:
    """一维卡尔曼滤波器

    Attributes:
        Q: 过程噪声协方差（process_noise），表示系统模型的不确定性
        R: 测量噪声协方差（measurement_noise），表示传感器测量误差
        P: 估计误差协方差
        X: 当前状态估计值
        K: 卡尔曼增益
    """

    def __init__(self, process_noise: float = 0.1,
                 measurement_noise: float = 0.5,
                 estimation_error: float = 1.0):
        """初始化卡尔曼滤波器

        Args:
            process_noise: 过程噪声，值越大滤波器越"信任"新测量值
            measurement_noise: 测量噪声，值越大滤波越平滑但响应越慢
            estimation_error: 初始估计误差
        """
        self.Q = process_noise
        self.R = measurement_noise
        self.P = estimation_error
        self.X = 0.0
        self.K = 0.0
        self._initialized = False

    def update(self, measurement: float) -> float:
        """更新滤波器，返回平滑后的值

        算法步骤：
        1. 预测：P = P + Q
        2. 更新卡尔曼增益：K = P / (P + R)
        3. 更新估计值：X = X + K * (measurement - X)
        4. 更新误差协方差：P = (1 - K) * P

        Args:
            measurement: 当前测量值（RSSI）

        Returns:
            平滑后的估计值
        """
        if not self._initialized:
            self.X = measurement
            self._initialized = True
            return self.X

        # 预测步骤
        self.P += self.Q

        # 测量更新步骤
        self.K = self.P / (self.P + self.R)
        self.X += self.K * (measurement - self.X)
        self.P *= (1.0 - self.K)

        return self.X

    def reset(self):
        """重置滤波器到初始状态"""
        self.P = 1.0
        self.X = 0.0
        self.K = 0.0
        self._initialized = False

    def get_state(self) -> float:
        """获取当前估计值，不进行更新"""
        return self.X
