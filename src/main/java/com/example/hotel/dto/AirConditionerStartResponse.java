package com.example.hotel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AirConditionerStartResponse {
    private boolean success;        // 是否成功
    private String message;         // 状态消息
    private Integer assignedAcId;   // 分配的空调ID，0表示等待队列，null表示失败
    private String mode;            // 空调模式
    private String fanSpeed;        // 风速
    private Double targetTemp;      // 目标温度
    
    // 成功分配空调的构造方法
    public static AirConditionerStartResponse success(Integer acId, String mode, String fanSpeed, Double targetTemp) {
        String message = acId == 0 ? "空调已开启，正在等待队列中" : "空调已开启，已分配空调";
        return new AirConditionerStartResponse(true, message, acId, mode, fanSpeed, targetTemp);
    }
    
    // 失败的构造方法
    public static AirConditionerStartResponse failure(String message) {
        return new AirConditionerStartResponse(false, message, null, null, null, null);
    }
} 