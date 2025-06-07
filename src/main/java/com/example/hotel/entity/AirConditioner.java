package com.example.hotel.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "air_conditioner")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirConditioner {
    @Id
    @Column(name = "ac_id")
    private Integer acId;                // 空调ID（1-3）
    
    @Column(name = "serving_room_id")
    private Integer servingRoomId;       // 当前服务的房间ID，null表示空闲
    
    @Column(name = "is_on")
    private Boolean on;                  // 是否开启
    
    @Enumerated(EnumType.STRING)
    @Column(name = "mode")
    private Mode mode;                   // 模式：制热/制冷
    
    @Enumerated(EnumType.STRING)
    @Column(name = "fan_speed")
    private FanSpeed fanSpeed;           // 风速：高/中/低
    
    @Column(name = "target_temp")
    private Double targetTemp;           // 目标温度
    
    @Column(name = "current_temp")
    private Double currentTemp;          // 当前温度
    
    @Column(name = "request_time")
    private LocalDateTime requestTime;   // 请求时间
    
    @Column(name = "service_start_time")
    private LocalDateTime serviceStartTime; // 服务开始时间
    
    @Column(name = "service_end_time")
    private LocalDateTime serviceEndTime;   // 服务结束时间
    
    @Column(name = "service_duration")
    private Integer serviceDuration;     // 服务时长（分钟）
    
    @Column(name = "cost")
    private Double cost;                 // 当前费用
    
    @Column(name = "priority")
    private Integer priority;            // 优先级，基于风速
    
    @Column(name = "service_time")
    private Integer serviceTime;         // 当前已服务时间（分钟）
    
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