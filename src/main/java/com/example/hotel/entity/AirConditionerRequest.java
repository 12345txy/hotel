package com.example.hotel.entity;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirConditionerRequest {
    private Integer roomId;                // 房间ID
    private AirConditioner.Mode mode;      // 模式：制热/制冷
    private AirConditioner.FanSpeed fanSpeed; // 风速：高/中/低
    private double targetTemp;             // 目标温度
    private double currentRoomTemp;        // 房间当前温度
    private LocalDateTime requestTime;     // 请求时间
    private Integer assignedAcId;          // 分配的空调ID，null表示未分配
    private int priority;                  // 优先级，基于风速
    private boolean active;                // 请求是否激活
}
