package com.example.hotel.controller;

import com.example.hotel.entity.AirConditioner;
import com.example.hotel.service.AirConditionerService;
import com.example.hotel.service.AirConditionerSchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ac")
public class AirConditionerController {
    
    private final AirConditionerService acService;
    private final AirConditionerSchedulerService schedulerService;
    
    @Autowired
    public AirConditionerController(AirConditionerService acService, 
                                   AirConditionerSchedulerService schedulerService) {
        this.acService = acService;
        this.schedulerService = schedulerService;
    }
    
    @PostMapping("/{roomId}/turnOn")
    public ResponseEntity<String> turnOn(
            @PathVariable Integer roomId,
            @RequestParam AirConditioner.Mode mode,
            @RequestParam(required = false) AirConditioner.FanSpeed fanSpeed,
            @RequestParam(required = false) Double targetTemp) {
        
        // 使用中风速作为默认值
        if (fanSpeed == null) {
            fanSpeed = AirConditioner.FanSpeed.MEDIUM;
        }
        
        boolean success = acService.turnOn(roomId, mode, fanSpeed, targetTemp != null ? targetTemp : 0);
        if (success) {
            schedulerService.addRequest(roomId);
            return ResponseEntity.ok("空调已开启");
        } else {
            return ResponseEntity.badRequest().body("开启空调失败");
        }
    }
    
    @PostMapping("/{roomId}/turnOff")
    public ResponseEntity<String> turnOff(@PathVariable Integer roomId) {
        boolean success = acService.turnOff(roomId);
        if (success) {
            schedulerService.removeRequest(roomId);
            return ResponseEntity.ok("空调已关闭");
        } else {
            return ResponseEntity.badRequest().body("关闭空调失败");
        }
    }
    
    @PostMapping("/{roomId}/adjustTemp")
    public ResponseEntity<String> adjustTemperature(
            @PathVariable Integer roomId,
            @RequestParam double targetTemp) {
        boolean success = acService.adjustTemperature(roomId, targetTemp);
        if (success) {
            return ResponseEntity.ok("温度已调整");
        } else {
            return ResponseEntity.badRequest().body("调整温度失败");
        }
    }
    
    @PostMapping("/{roomId}/adjustFanSpeed")
    public ResponseEntity<String> adjustFanSpeed(
            @PathVariable Integer roomId,
            @RequestParam AirConditioner.FanSpeed fanSpeed) {
        boolean success = acService.adjustFanSpeed(roomId, fanSpeed);
        if (success) {
            // 更新优先级
            schedulerService.removeRequest(roomId);
            schedulerService.addRequest(roomId);
            return ResponseEntity.ok("风速已调整");
        } else {
            return ResponseEntity.badRequest().body("调整风速失败");
        }
    }
    
    @GetMapping("/{roomId}")
    public ResponseEntity<AirConditioner> getStatus(@PathVariable Integer roomId) {
        AirConditioner ac = acService.getAirConditionerStatus(roomId);
        if (ac != null) {
            return ResponseEntity.ok(ac);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
} 