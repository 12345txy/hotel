package com.example.hotel.controller;

import com.example.hotel.entity.AirConditioner;
import com.example.hotel.entity.AirConditionerRequest;
import com.example.hotel.service.AirConditionerService;
import com.example.hotel.service.AirConditionerSchedulerService;
import com.example.hotel.dto.AirConditionerStartResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.time.Duration;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/ac")
public class AirConditionerController {
    
    private final AirConditionerService acService;
    private final AirConditionerSchedulerService schedulerService;
    
    public AirConditionerController(AirConditionerService acService, 
                                  AirConditionerSchedulerService schedulerService) {
        this.acService = acService;
        this.schedulerService = schedulerService;
    }
    
    // 获取所有空调状态
    @GetMapping("/all")
    public ResponseEntity<List<AirConditioner>> getAllAirConditioners() {
        return ResponseEntity.ok(acService.getAllAirConditioners());
    }
    
    // 获取单个空调状态
    @GetMapping("/{acId}")
    public ResponseEntity<AirConditioner> getAirConditioner(@PathVariable Integer acId) {
        AirConditioner ac = acService.getAirConditioner(acId);
        if (ac != null) {
            return ResponseEntity.ok(ac);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    // 获取房间的空调请求状态
    @GetMapping("/room/{roomId}")
    public ResponseEntity<AirConditionerRequest> getRoomRequest(@PathVariable Integer roomId) {
        AirConditionerRequest request = acService.getRoomRequest(roomId);
        
        // 方案1: 总是返回200，即使是null
        return ResponseEntity.ok(request);
        
        // 方案2: 如果需要区分不同状态，可以用以下逻辑
        /*
        if (request != null && request.isActive()) {
            return ResponseEntity.ok(request);
        } else if (request != null && !request.isActive()) {
            // 请求存在但已结束，返回410 Gone
            return ResponseEntity.status(HttpStatus.GONE).build();
        } else {
            // 没有请求记录，返回空数据而不是404
            return ResponseEntity.ok().build(); // 200 with empty body
        }
        */
    }
    
    // 开启空调（使用固定默认参数：制冷模式，中风，25度）
    @PostMapping("/room/{roomId}/start")
    public ResponseEntity<AirConditionerStartResponse> startAirConditioner(@PathVariable Integer roomId) {
        
        // 固定的默认参数
        AirConditioner.Mode mode = AirConditioner.Mode.COOLING;
        AirConditioner.FanSpeed fanSpeed = AirConditioner.FanSpeed.MEDIUM;
        Double targetTemp = 25.0;
        
        Integer assignedAcId = acService.createRequest(roomId, mode, fanSpeed, targetTemp);
        if (assignedAcId == null) {
            return ResponseEntity.badRequest()
                    .body(AirConditionerStartResponse.failure("开启空调失败：房间不存在"));
        } else {
            return ResponseEntity.ok(AirConditionerStartResponse.success(
                    assignedAcId, 
                    mode.toString(), 
                    fanSpeed.toString(), 
                    targetTemp
            ));
        }
    }
    
    // 调整空调设置（模式、温度、风速都是可选参数）
    @PutMapping("/room/{roomId}/adjust")
    public ResponseEntity<String> adjustAirConditioner(
            @PathVariable Integer roomId,
            @RequestParam(required = false) AirConditioner.Mode mode,
            @RequestParam(required = false) AirConditioner.FanSpeed fanSpeed,
            @RequestParam(required = false) Double targetTemp) {
        
        // 检查房间是否有活跃的空调请求
        AirConditionerRequest currentRequest = acService.getRoomRequest(roomId);
        if (currentRequest == null || !currentRequest.isActive()) {
            return ResponseEntity.badRequest().body("调整失败：房间没有活跃的空调请求，请先开启空调");
        }
        
        // 至少需要提供一个参数
        if (mode == null && fanSpeed == null && targetTemp == null) {
            return ResponseEntity.badRequest().body("调整失败：至少需要提供一个参数（mode、fanSpeed或targetTemp）");
        }
        
        boolean success = acService.adjustAirConditionerSettings(roomId, mode, fanSpeed, targetTemp);
        if (success) {
            return ResponseEntity.ok("空调设置已调整");
        } else {
            return ResponseEntity.badRequest().body("调整空调设置失败");
        }
    }
    
    // 关闭房间空调（不管是正在服务还是等待中的请求）
    @PostMapping("/room/{roomId}/cancel")
    public ResponseEntity<String> cancelRequest(@PathVariable Integer roomId) {
        boolean success = acService.cancelRequest(roomId);
        if (success) {
            return ResponseEntity.ok("空调已关闭");
        } else {
            return ResponseEntity.badRequest().body("关闭空调失败：房间没有正在运行的空调");
        }
    }
    
    // 获取当前等待队列
    @GetMapping("/queue/waiting")
    public ResponseEntity<List<Integer>> getWaitingQueue() {
        List<Integer> waitingRooms = schedulerService.getWaitingRooms();
        return ResponseEntity.ok(waitingRooms);
    }
    
    // 获取当前服务集合
    @GetMapping("/queue/service")
    public ResponseEntity<List<Integer>> getServiceSet() {
        List<Integer> serviceRooms = schedulerService.getServiceRooms();
        return ResponseEntity.ok(serviceRooms);
    }
    
    // 获取所有房间的空调分配情况
    @GetMapping("/rooms/assignment")
    public ResponseEntity<Map<String, Object>> getRoomsAcAssignment() {
        List<com.example.hotel.entity.Room> rooms = acService.getAllRoomsWithAcAssignment();
        Map<String, Object> result = new HashMap<>();
        result.put("rooms", rooms);
        result.put("total", rooms.size());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/queue/status")
    public ResponseEntity<Map<String, Object>> getQueueStatus() {
        Map<String, Object> response = new HashMap<>();
        
        // 获取所有活跃请求
        List<AirConditionerRequest> activeRequests = acService.getAllActiveRequests();
        
        // 分类为服务队列和等待队列
        List<Map<String, Object>> serviceQueue = new ArrayList<>();
        List<Map<String, Object>> waitingQueue = new ArrayList<>();
        
        for (AirConditionerRequest request : activeRequests) {
            Map<String, Object> requestInfo = new HashMap<>();
            requestInfo.put("roomId", request.getRoomId());
            requestInfo.put("mode", request.getMode());
            requestInfo.put("fanSpeed", request.getFanSpeed());
            requestInfo.put("targetTemp", request.getTargetTemp());
            requestInfo.put("currentRoomTemp", request.getCurrentRoomTemp());
            requestInfo.put("priority", request.getPriority());
            requestInfo.put("requestTime", request.getRequestTime());
            
            if (request.getAssignedAcId() != null) {
                // 在服务队列中
                requestInfo.put("assignedAcId", request.getAssignedAcId());
                requestInfo.put("status", "服务中");
                
                // 获取服务时长（使用调度器的计数器）
                Integer serviceTime = schedulerService.getServiceTime(request.getRoomId());
                requestInfo.put("serviceTime", serviceTime != null ? serviceTime : 0);
                
                serviceQueue.add(requestInfo);
            } else {
                // 在等待队列中
                requestInfo.put("status", "等待中");
                Integer waitingTime = schedulerService.getWaitingTime(request.getRoomId());
                requestInfo.put("waitingTime", waitingTime != null ? waitingTime : 0);
                waitingQueue.add(requestInfo);
            }
        }
        
        // 按优先级和请求时间排序等待队列
        waitingQueue.sort((a, b) -> {
            int priorityCompare = Integer.compare((Integer) b.get("priority"), (Integer) a.get("priority"));
            if (priorityCompare != 0) return priorityCompare;
            return ((LocalDateTime) a.get("requestTime")).compareTo((LocalDateTime) b.get("requestTime"));
        });
        
        // 获取空调状态
        List<Map<String, Object>> airConditioners = new ArrayList<>();
        for (AirConditioner ac : acService.getAllAirConditioners()) {
            Map<String, Object> acInfo = new HashMap<>();
            acInfo.put("acId", ac.getAcId());
            acInfo.put("isOn", ac.getOn() != null && ac.getOn());
            acInfo.put("servingRoomId", ac.getServingRoomId());
            acInfo.put("mode", ac.getMode());
            acInfo.put("fanSpeed", ac.getFanSpeed());
            acInfo.put("targetTemp", ac.getTargetTemp());
            acInfo.put("currentTemp", ac.getCurrentTemp());
            
            if (ac.getServiceStartTime() != null) {
                long serviceMinutes = Duration.between(ac.getServiceStartTime(), LocalDateTime.now()).toMinutes();
                acInfo.put("serviceTime", serviceMinutes);
            } else {
                acInfo.put("serviceTime", 0);
            }
            
            airConditioners.add(acInfo);
        }
        
        response.put("serviceQueue", serviceQueue);
        response.put("waitingQueue", waitingQueue);
        response.put("airConditioners", airConditioners);
        response.put("totalRequests", activeRequests.size());
        response.put("serviceCount", serviceQueue.size());
        response.put("waitingCount", waitingQueue.size());
        response.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(response);
    }
    
    // 调试接口：手动触发队列同步和调度
    @PostMapping("/debug/resync")
    public ResponseEntity<String> resyncQueues() {
        try {
            // 重新同步等待队列
            schedulerService.resyncWaitingQueue();
            return ResponseEntity.ok("队列同步完成，等待队列已重新处理");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("队列同步失败：" + e.getMessage());
        }
    }
} 