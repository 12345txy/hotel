package com.example.hotel.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "room")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room {
    @Id
    private Integer roomId;           // 房间号
    
    private double price;             // 每日价格
    private double initialTemp;       // 初始温度
    private double currentTemp;       // 当前温度
    private boolean occupied;         // 是否被占用
    private LocalDateTime checkInTime; // 入住时间
    private LocalDateTime checkOutTime; // 退房时间
    
    @Transient  // 不保存到数据库
    private Guest guest;              // 入住客人信息
} 