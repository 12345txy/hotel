package com.example.hotel.controller;

import com.example.hotel.entity.Guest;
import com.example.hotel.service.CheckInService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkin")
public class CheckInController {
    
    private final CheckInService checkInService;
    
    @Autowired
    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }
    
    @PostMapping("/{roomId}")
    public ResponseEntity<String> checkIn(@PathVariable Integer roomId, @RequestBody Guest guest) {
        boolean success = checkInService.checkIn(roomId, guest);
        if (success) {
            return ResponseEntity.ok("入住成功");
        } else {
            return ResponseEntity.badRequest().body("房间不可用");
        }
    }
    
    @PostMapping("/checkout/{roomId}")
    public ResponseEntity<String> checkOut(@PathVariable Integer roomId) {
        boolean success = checkInService.checkOut(roomId);
        if (success) {
            return ResponseEntity.ok("退房成功");
        } else {
            return ResponseEntity.badRequest().body("退房失败");
        }
    }
} 