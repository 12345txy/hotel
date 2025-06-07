package com.example.hotel.entity;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "guest")
public class Guest {
    @Id
    @Column(length = 50)
    private String id;        // 身份证号
    private String name;      // 姓名
    private String phone;     // 电话号码
    @Transient  // 不保存到数据库
    private Integer stayDays;
} 