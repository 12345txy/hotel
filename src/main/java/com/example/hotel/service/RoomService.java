package com.example.hotel.service;

import com.example.hotel.entity.Room;
import com.example.hotel.repository.RoomRepository;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

@Service
public class RoomService {
    private final RoomRepository roomRepository;
    // 保留内存缓存以提高性能
    private final Map<Integer, Room> rooms = new HashMap<>();
    
    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }
    
    // 初始化房间数据
    @PostConstruct
    public void initializeRooms() {
        // 检查数据库中是否已有房间数据
        if (roomRepository.count() == 0) {
        // 制冷模式下的初始房间温度
            List<Room> initialRooms = List.of(
                Room.builder().roomId(1).price(100).initialTemp(32).currentTemp(32).occupied(false).build(),
                Room.builder().roomId(2).price(125).initialTemp(28).currentTemp(28).occupied(false).build(),
                Room.builder().roomId(3).price(150).initialTemp(30).currentTemp(30).occupied(false).build(),
                Room.builder().roomId(4).price(200).initialTemp(29).currentTemp(29).occupied(false).build(),
                Room.builder().roomId(5).price(100).initialTemp(35).currentTemp(35).occupied(false).build()
            );
            
            // 保存到数据库
            roomRepository.saveAll(initialRooms);
        }
        
        // 加载到内存缓存
        loadRoomsToCache();
    }
    
    // 加载房间数据到内存缓存
    private void loadRoomsToCache() {
        List<Room> allRooms = roomRepository.findAll();
        rooms.clear();
        for (Room room : allRooms) {
            rooms.put(room.getRoomId(), room);
        }
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
    // 获取所有空闲房间的 roomId
    public List<Integer> getAvailableRoomIds() {
        List<Integer> availableRoomIds = new ArrayList<>();
        for (Map.Entry<Integer, Room> entry : rooms.entrySet()) {
            if (!entry.getValue().isOccupied()) {
                availableRoomIds.add(entry.getKey());
            }
        }
        return availableRoomIds;
    }
    // 检查房间是否可用
    public boolean isRoomAvailable(Integer roomId) {
        Room room = rooms.get(roomId);
        return room != null && !room.isOccupied();
    }
    
    // 保存房间信息
    public void saveRoom(Room room) {
        if (room != null && room.getRoomId() != null) {
            // 保存到数据库
            Room savedRoom = roomRepository.save(room);
            // 更新内存缓存
            rooms.put(savedRoom.getRoomId(), savedRoom);
        }
    }
} 