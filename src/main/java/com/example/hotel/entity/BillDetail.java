package com.example.hotel.entity;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillDetail {
    private Integer roomId;                  // 房间号
    private LocalDateTime requestTime;       // 请求时间
    private LocalDateTime serviceStartTime;  // 服务开始时间
    private LocalDateTime serviceEndTime;    // 服务结束时间
    private int serviceDuration;             // 服务时长(分钟)
    private AirConditioner.FanSpeed fanSpeed; // 风速
    private double cost;                     // 当前费用
    private double rate;                     // 费率(元/度)
} 