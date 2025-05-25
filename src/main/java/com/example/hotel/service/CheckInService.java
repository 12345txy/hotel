package com.example.hotel.service;

import com.example.hotel.entity.Guest;
import com.example.hotel.entity.Room;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CheckInService {
    
    private final RoomService roomService;
    
    @Autowired
    public CheckInService(RoomService roomService) {
        this.roomService = roomService;
    }
    
    // 办理入住
    public boolean checkIn(Integer roomId, Guest guest) {
        return roomService.getRoomById(roomId).map(room -> {
            room.setOccupied(true);
            room.setGuest(guest);
            room.setCheckInTime(LocalDateTime.now());
            return true;
        }).orElse(false);
    }
    

} 