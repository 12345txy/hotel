package com.example.hotel.controller;

import com.example.hotel.entity.Guest;
import com.example.hotel.service.CheckInService;
import com.example.hotel.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/checkin")
public class CheckInController {

    private final CheckInService checkInService;
    private final RoomService roomService;

    @Autowired
    public CheckInController(CheckInService checkInService, RoomService roomService) {
        this.checkInService = checkInService;
        this.roomService = roomService;
    }

    // 获取所有空闲房间号
    @GetMapping("/available-rooms")
    public ResponseEntity<List<Integer>> getAvailableRooms() {
        List<Integer> availableRoomIds = roomService.getAvailableRoomIds();
        return ResponseEntity.ok(availableRoomIds);
    }

    // 顾客入住指定房间
    @PostMapping("/{roomId}")
    public ResponseEntity<String> checkIn(@PathVariable Integer roomId, @RequestBody Guest guest) {
        boolean success = checkInService.checkIn(roomId, guest);
        if (success) {
            return ResponseEntity.ok("成功入住房间: " + roomId);
        } else {
            return ResponseEntity.badRequest().body("房间不可用，请重试");
        }
    }
}