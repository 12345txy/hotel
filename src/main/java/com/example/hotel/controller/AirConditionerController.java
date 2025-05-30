package com.example.hotel.controller;

import com.example.hotel.entity.AirConditioner;
import com.example.hotel.entity.AirConditionerRequest;
import com.example.hotel.service.AirConditionerService;
import com.example.hotel.service.AirConditionerSchedulerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
        if (request != null && request.isActive()) {
            return ResponseEntity.ok(request);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    // 房间请求开启空调
    @PostMapping("/room/{roomId}/request")
    public ResponseEntity<String> requestAirConditioner(
            @PathVariable Integer roomId,
            @RequestParam AirConditioner.Mode mode,
            @RequestParam(required = false) AirConditioner.FanSpeed fanSpeed,
            @RequestParam(required = false) Double targetTemp) {
        
        // 使用中风速作为默认值
        if (fanSpeed == null) {
            fanSpeed = AirConditioner.FanSpeed.MEDIUM;
        }
        
        boolean success = acService.createRequest(roomId, mode, fanSpeed, targetTemp != null ? targetTemp : 0);
        if (success) {
            schedulerService.addRequest(roomId);
            return ResponseEntity.ok("空调请求已提交");
        } else {
            return ResponseEntity.badRequest().body("提交空调请求失败");
        }
    }
    
    // 取消房间的空调请求
    @PostMapping("/room/{roomId}/cancel")
    public ResponseEntity<String> cancelRequest(@PathVariable Integer roomId) {
        boolean success = acService.cancelRequest(roomId);
        if (success) {
            schedulerService.removeRequest(roomId);
            return ResponseEntity.ok("空调请求已取消");
        } else {
            return ResponseEntity.badRequest().body("取消空调请求失败");
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
} 