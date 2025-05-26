package com.example.hotel.entity;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.Id;
import javax.persistence.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "guest")
public class Guest {
    @Id
    private String id;        // 身份证号
    private String name;      // 姓名
    private String phone;     // 电话号码
} 