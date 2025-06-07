package com.example.hotel.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 日志工具类
 * 提供统一的日志记录方法和性能监控功能
 */
public class LoggerUtil {
    
    private static final Logger performanceLogger = LoggerFactory.getLogger("performance");
    private static final ConcurrentMap<String, Long> performanceCounters = new ConcurrentHashMap<>();
    
    /**
     * 记录空调操作日志
     */
    public static void logAirConditionerOperation(String operation, Integer roomId, Integer acId, Object... params) {
        Logger logger = LoggerFactory.getLogger("com.example.hotel.service.AirConditionerService");
        
        // 设置MDC上下文
        MDC.put("operation", operation);
        MDC.put("roomId", String.valueOf(roomId));
        if (acId != null) {
            MDC.put("acId", String.valueOf(acId));
        }
        
        StringBuilder message = new StringBuilder();
        message.append("空调操作: ").append(operation);
        message.append(", 房间: ").append(roomId);
        if (acId != null) {
            message.append(", 空调: ").append(acId);
        }
        
        if (params != null && params.length > 0) {
            message.append(", 参数: ");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) message.append(", ");
                message.append(params[i]);
            }
        }
        
        logger.info(message.toString());
        
        // 清理MDC上下文
        MDC.clear();
    }
    
    /**
     * 记录调度操作日志
     */
    public static void logSchedulerOperation(String operation, Object... params) {
        Logger logger = LoggerFactory.getLogger("com.example.hotel.service.AirConditionerSchedulerService");
        
        MDC.put("operation", operation);
        
        StringBuilder message = new StringBuilder();
        message.append("调度操作: ").append(operation);
        
        if (params != null && params.length > 0) {
            message.append(", 参数: ");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) message.append(", ");
                message.append(params[i]);
            }
        }
        
        logger.debug(message.toString());
        
        MDC.clear();
    }
    
    /**
     * 记录API请求日志
     */
    public static void logApiRequest(String endpoint, String method, Object requestBody) {
        Logger logger = LoggerFactory.getLogger("com.example.hotel.controller");
        
        MDC.put("endpoint", endpoint);
        MDC.put("method", method);
        
        if (requestBody != null) {
            logger.info("API请求: {} {}, 请求体: {}", method, endpoint, requestBody);
        } else {
            logger.info("API请求: {} {}", method, endpoint);
        }
        
        MDC.clear();
    }
    
    /**
     * 记录API响应日志
     */
    public static void logApiResponse(String endpoint, String method, int statusCode, Object responseBody) {
        Logger logger = LoggerFactory.getLogger("com.example.hotel.controller");
        
        MDC.put("endpoint", endpoint);
        MDC.put("method", method);
        MDC.put("statusCode", String.valueOf(statusCode));
        
        if (responseBody != null) {
            logger.info("API响应: {} {}, 状态码: {}, 响应体: {}", method, endpoint, statusCode, responseBody);
        } else {
            logger.info("API响应: {} {}, 状态码: {}", method, endpoint, statusCode);
        }
        
        MDC.clear();
    }
    
    /**
     * 记录错误日志
     */
    public static void logError(Class<?> clazz, String operation, Throwable throwable, Object... params) {
        Logger logger = LoggerFactory.getLogger(clazz);
        
        MDC.put("operation", operation);
        MDC.put("errorType", throwable.getClass().getSimpleName());
        
        StringBuilder message = new StringBuilder();
        message.append("操作失败: ").append(operation);
        
        if (params != null && params.length > 0) {
            message.append(", 参数: ");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) message.append(", ");
                message.append(params[i]);
            }
        }
        
        logger.error(message.toString(), throwable);
        
        MDC.clear();
    }
    
    /**
     * 开始性能监控
     */
    public static void startPerformanceMonitor(String operationName) {
        String key = Thread.currentThread().getName() + ":" + operationName;
        performanceCounters.put(key, System.currentTimeMillis());
        
        MDC.put("operation", operationName);
        performanceLogger.info("开始监控操作: {}", operationName);
    }
    
    /**
     * 结束性能监控
     */
    public static void endPerformanceMonitor(String operationName) {
        String key = Thread.currentThread().getName() + ":" + operationName;
        Long startTime = performanceCounters.remove(key);
        
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            
            MDC.put("operation", operationName);
            MDC.put("duration", String.valueOf(duration));
            
            performanceLogger.info("操作完成: {}, 耗时: {}ms", operationName, duration);
            
            // 如果操作耗时超过1秒，记录警告
            if (duration > 1000) {
                performanceLogger.warn("操作耗时过长: {}, 耗时: {}ms", operationName, duration);
            }
        }
        
        MDC.clear();
    }
    
    /**
     * 记录业务流程日志
     */
    public static void logBusinessFlow(String flow, String step, Object... params) {
        Logger logger = LoggerFactory.getLogger("com.example.hotel.business");
        
        MDC.put("flow", flow);
        MDC.put("step", step);
        
        StringBuilder message = new StringBuilder();
        message.append("业务流程: ").append(flow);
        message.append(", 步骤: ").append(step);
        
        if (params != null && params.length > 0) {
            message.append(", 参数: ");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) message.append(", ");
                message.append(params[i]);
            }
        }
        
        logger.info(message.toString());
        
        MDC.clear();
    }
    
    /**
     * 记录温度变化日志
     */
    public static void logTemperatureChange(Integer roomId, double oldTemp, double newTemp, String reason) {
        Logger logger = LoggerFactory.getLogger("com.example.hotel.temperature");
        
        MDC.put("roomId", String.valueOf(roomId));
        MDC.put("oldTemp", String.valueOf(oldTemp));
        MDC.put("newTemp", String.valueOf(newTemp));
        MDC.put("reason", reason);
        
        logger.info("房间温度变化: 房间{}, {}°C -> {}°C, 原因: {}", 
                   roomId, oldTemp, newTemp, reason);
        
        MDC.clear();
    }
    
    /**
     * 记录资源使用情况
     */
    public static void logResourceUsage(String resourceType, String usage) {
        Logger logger = LoggerFactory.getLogger("com.example.hotel.resource");
        
        MDC.put("resourceType", resourceType);
        MDC.put("usage", usage);
        
        logger.info("资源使用情况: {}, 详情: {}", resourceType, usage);
        
        MDC.clear();
    }
} 