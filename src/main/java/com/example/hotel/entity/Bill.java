package com.example.hotel.entity;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bill {
    private Long id;
    private Integer roomId;                  // 房间号
    private LocalDateTime checkInTime;       // 入住时间
    private LocalDateTime checkOutTime;      // 退房时间
    private Integer daysOfStay;              // 入住天数（新增字段）
    private double totalCost;                // 总费用
    private double roomCost;                 // 房费
    private double acCost;                   // 空调费用
    @Transient  // 不保存到数据库
    private List<BillDetail> details;        // 账单明细
} 