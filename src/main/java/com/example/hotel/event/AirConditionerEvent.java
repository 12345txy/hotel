package com.example.hotel.event;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;

/**
 * 空调事件基类
 */
@Data
@Builder
public class AirConditionerEvent {
    private Integer roomId;
    private Integer acId;
    private EventType eventType;
    private LocalDateTime timestamp;
    private Object payload; // 额外数据
    
    public enum EventType {
        REQUEST_CREATED,        // 新请求创建
        REQUEST_CANCELLED,      // 请求取消
        PRIORITY_CHANGED,       // 优先级变更
        TARGET_TEMP_REACHED,    // 达到目标温度
        AC_ASSIGNED,           // 空调分配
        AC_RELEASED,           // 空调释放
        EVICTED_FROM_SERVICE   // 被抢占
    }
} 