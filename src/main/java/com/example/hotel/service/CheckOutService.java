package com.example.hotel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
@Service
public class CheckOutService {
    private final RoomService roomService;

    @Autowired
    public CheckOutService(RoomService roomService) {
        this.roomService = roomService;
    }

    // 办理退房
    public boolean checkOut(Integer roomId) {
        return roomService.getRoomById(roomId).map(room -> {
            if (!room.isOccupied()) {
                return false;
            }

            room.setOccupied(false);
            room.setCheckOutTime(LocalDateTime.now());
            room.setGuest(null);
            return true;
        }).orElse(false);
    }
}
