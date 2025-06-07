package com.example.hotel.entity;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bill_detail")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "detail_id")
    private Long id;                         // 详单ID

    @Column(name = "bill_id")
    private Long billId;                     // 关联的账单ID

    @Column(name = "room_id")
    private Integer roomId;                  // 房间号

    @Column(name = "ac_id")
    private Integer acId;                    // 空调ID

    @Column(name = "request_time")
    private LocalDateTime requestTime;       // 请求时间

    @Column(name = "service_start_time")
    private LocalDateTime serviceStartTime;  // 服务开始时间

    @Column(name = "service_end_time")
    private LocalDateTime serviceEndTime;    // 服务结束时间

    @Column(name = "service_duration")
    private int serviceDuration;             // 服务时长(分钟)

    @Enumerated(EnumType.STRING)
    @Column(name = "fan_speed")
    private AirConditioner.FanSpeed fanSpeed; // 风速

    @Enumerated(EnumType.STRING)
    @Column(name = "mode")
    private AirConditioner.Mode mode;        // 模式

    @Column(name = "target_temp")
    private double targetTemp;               // 目标温度

    @Column(name = "temp_change")
    private double tempChange;               // 温度变化量

    @Column(name = "energy_consumed")
    private double energyConsumed;           // 能耗(度)

    @Column(name = "cost")
    private double cost;                     // 当前费用

    @Column(name = "rate")
    private double rate;                     // 费率(元/度)

    @Column(name = "created_at")
    private LocalDateTime createdAt;         // 创建时间
} 