package com.example.hotel.entity;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "bill")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bill_id")
    private Long id;
    
    @Column(name = "room_id")
    private Integer roomId;                  // 房间号
    
    @Column(name = "guest_id", length = 50)
    private String guestId;                  // 客人身份证号
    
    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;       // 入住时间
    
    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;      // 退房时间
    
    @Column(name = "days_of_stay")
    private Integer daysOfStay;              // 入住天数（新增字段）
    
    @Column(name = "total_cost")
    private double totalCost;                // 总费用
    
    @Column(name = "room_cost")
    private double roomCost;                 // 房费
    
    @Column(name = "ac_cost")
    private double acCost;                   // 空调费用
    
    @Column(name = "other_cost")
    private double otherCost;                // 其他费用
    
    @Column(name = "bill_status")
    private String billStatus;               // 账单状态
    
    @Column(name = "payment_time")
    private LocalDateTime paymentTime;       // 支付时间

    @OneToMany(mappedBy = "billId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<BillDetail> details;        // 账单明细
} 