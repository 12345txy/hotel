package com.example.hotel.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "air_conditioner_request")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirConditionerRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;                       // 请求ID
    
    @Column(name = "room_id")
    private Integer roomId;                // 房间ID
    
    @Enumerated(EnumType.STRING)
    @Column(name = "mode")
    private AirConditioner.Mode mode;      // 模式：制热/制冷
    
    @Enumerated(EnumType.STRING)
    @Column(name = "fan_speed")
    private AirConditioner.FanSpeed fanSpeed; // 风速：高/中/低
    
    @Column(name = "target_temp")
    private double targetTemp;             // 目标温度
    
    @Column(name = "current_room_temp")
    private double currentRoomTemp;        // 房间当前温度
    
    @Column(name = "request_time")
    private LocalDateTime requestTime;     // 请求时间
    
    @Column(name = "assigned_ac_id")
    private Integer assignedAcId;          // 分配的空调ID，null表示未分配
    
    @Column(name = "priority")
    private int priority;                  // 优先级，基于风速
    
    @Column(name = "active")
    private boolean active;                // 请求是否激活
}
