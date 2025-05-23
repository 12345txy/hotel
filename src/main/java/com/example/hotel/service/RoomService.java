package com.example.hotel.service;

import com.example.hotel.entity.Room;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

@Service
public class RoomService {
    // 模拟数据库存储
    private final Map<Integer, Room> rooms = new HashMap<>();
    
    // 初始化房间数据
    public void initializeRooms() {
        // 制冷模式下的初始房间温度
        rooms.put(1, Room.builder().roomId(1).price(100).initialTemp(32).currentTemp(32).occupied(false).build());
        rooms.put(2, Room.builder().roomId(2).price(125).initialTemp(28).currentTemp(28).occupied(false).build());
        rooms.put(3, Room.builder().roomId(3).price(150).initialTemp(30).currentTemp(30).occupied(false).build());
        rooms.put(4, Room.builder().roomId(4).price(200).initialTemp(29).currentTemp(29).occupied(false).build());
        rooms.put(5, Room.builder().roomId(5).price(100).initialTemp(35).currentTemp(35).occupied(false).build());
    }
    
    // 获取所有房间
    public List<Room> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }
    
    // 获取指定房间
    public Optional<Room> getRoomById(Integer roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }
    
    // 更新房间温度
    public void updateRoomTemperature(Integer roomId, double temperature) {
        Room room = rooms.get(roomId);
        if (room != null) {
            room.setCurrentTemp(temperature);
            rooms.put(roomId, room);
        }
    }
    
    // 检查房间是否可用
    public boolean isRoomAvailable(Integer roomId) {
        Room room = rooms.get(roomId);
        return room != null && !room.isOccupied();
    }
} 