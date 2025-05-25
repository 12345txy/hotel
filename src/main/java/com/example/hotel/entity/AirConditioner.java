package com.example.hotel.entity;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirConditioner {
    private Integer roomId;              // 对应房间号
    private boolean on;                  // 是否开启
    private Mode mode;                   // 模式：制热/制冷
    private FanSpeed fanSpeed;           // 风速：高/中/低
    private double targetTemp;           // 目标温度
    private double currentTemp;          // 当前温度
    private LocalDateTime requestTime;   // 请求时间
    private LocalDateTime serviceStartTime; // 服务开始时间
    private LocalDateTime serviceEndTime;   // 服务结束时间
    private int serviceDuration;         // 服务时长（分钟）
    private double cost;                 // 当前费用
    private int priority;                // 优先级，基于风速
    private int serviceTime;             // 当前已服务时间（分钟）
    
    public enum Mode {
        COOLING, HEATING
    }
    
    @Getter
    public enum FanSpeed {
        HIGH(1), MEDIUM(2), LOW(3);
        
        private final int tempChangeTime; // 改变1度所需分钟数
        
        FanSpeed(int tempChangeTime) {
            this.tempChangeTime = tempChangeTime;
        }

        public int getPriority() {
            return 3 - ordinal(); // HIGH=3, MEDIUM=2, LOW=1
        }
    }
} 