package com.example.hotel.service;

import com.example.hotel.entity.AirConditioner;
import com.example.hotel.entity.AirConditionerRequest;
import com.example.hotel.entity.Room;
import com.example.hotel.repository.AirConditionerRepository;
import com.example.hotel.repository.AirConditionerRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 空调状态管理器
 * 负责统一管理空调、请求、房间状态的同步
 */
@Service
public class AirConditionerStateManager {
    
    private final AirConditionerRepository airConditionerRepository;
    private final AirConditionerRequestRepository requestRepository;
    private final RoomService roomService;
    
    // 内存缓存 - 仅用于提高查询性能
    private final Map<Integer, AirConditioner> airConditionerCache = new ConcurrentHashMap<>();
    private final Map<Integer, AirConditionerRequest> activeRequestCache = new ConcurrentHashMap<>();
    
    public AirConditionerStateManager(AirConditionerRepository airConditionerRepository,
                                    AirConditionerRequestRepository requestRepository,
                                    RoomService roomService) {
        this.airConditionerRepository = airConditionerRepository;
        this.requestRepository = requestRepository;
        this.roomService = roomService;
    }
    
    /**
     * 分配空调给房间 - 原子操作
     */
    @Transactional
    public boolean assignAirConditioner(Integer roomId, Integer acId) {
        AirConditionerRequest request = getActiveRequest(roomId);
        AirConditioner ac = getAirConditioner(acId);
        
        if (request == null || ac == null || ac.getServingRoomId() != null) {
            return false;
        }
        
        // 1. 更新空调状态
        ac.setOn(true);
        ac.setServingRoomId(roomId);
        ac.setMode(request.getMode());
        ac.setFanSpeed(request.getFanSpeed());
        ac.setTargetTemp(request.getTargetTemp());
        ac.setServiceStartTime(LocalDateTime.now());
        airConditionerRepository.save(ac);
        
        // 2. 更新请求状态
        request.setAssignedAcId(acId);
        requestRepository.save(request);
        
        // 3. 更新房间状态
        Room room = roomService.getRoomById(roomId).orElse(null);
        if (room != null) {
            room.setAssignedAcId(acId);
            roomService.saveRoom(room);
        }
        
        // 4. 同步缓存
        airConditionerCache.put(acId, ac);
        activeRequestCache.put(roomId, request);
        
        return true;
    }
    
    /**
     * 释放空调 - 原子操作
     */
    @Transactional
    public void releaseAirConditioner(Integer roomId) {
        AirConditionerRequest request = getActiveRequest(roomId);
        if (request == null || request.getAssignedAcId() == null) {
            return;
        }
        
        Integer acId = request.getAssignedAcId();
        AirConditioner ac = getAirConditioner(acId);
        
        // 1. 更新空调状态
        if (ac != null) {
            ac.setOn(false);
            ac.setServingRoomId(null);
            ac.setServiceEndTime(LocalDateTime.now());
            airConditionerRepository.save(ac);
            airConditionerCache.put(acId, ac);
        }
        
        // 2. 更新请求状态
        request.setAssignedAcId(null);
        requestRepository.save(request);
        activeRequestCache.put(roomId, request);
        
        // 3. 更新房间状态
        Room room = roomService.getRoomById(roomId).orElse(null);
        if (room != null) {
            room.setAssignedAcId(null);
            roomService.saveRoom(room);
        }
    }
    
    /**
     * 停用请求 - 原子操作
     */
    @Transactional
    public void deactivateRequest(Integer roomId) {
        AirConditionerRequest request = getActiveRequest(roomId);
        if (request == null) {
            return;
        }
        
        // 如果已分配空调，先释放
        if (request.getAssignedAcId() != null) {
            releaseAirConditioner(roomId);
        }
        
        // 停用请求
        request.setActive(false);
        requestRepository.save(request);
        activeRequestCache.remove(roomId);
    }
    
    /**
     * 获取活跃请求（优先从缓存）
     */
    public AirConditionerRequest getActiveRequest(Integer roomId) {
        // 先查缓存
        AirConditionerRequest cached = activeRequestCache.get(roomId);
        if (cached != null && cached.isActive()) {
            return cached;
        }
        
        // 查数据库并更新缓存
        AirConditionerRequest fromDb = requestRepository.findByRoomIdAndActiveTrue(roomId).orElse(null);
        if (fromDb != null) {
            activeRequestCache.put(roomId, fromDb);
        } else {
            activeRequestCache.remove(roomId);
        }
        return fromDb;
    }
    
    /**
     * 获取空调（优先从缓存）
     */
    public AirConditioner getAirConditioner(Integer acId) {
        // 先查缓存
        AirConditioner cached = airConditionerCache.get(acId);
        if (cached != null) {
            return cached;
        }
        
        // 查数据库并更新缓存
        AirConditioner fromDb = airConditionerRepository.findById(acId).orElse(null);
        if (fromDb != null) {
            airConditionerCache.put(acId, fromDb);
        }
        return fromDb;
    }
    
    /**
     * 同步所有缓存（用于系统启动）
     */
    public void syncCaches() {
        // 同步空调缓存
        airConditionerCache.clear();
        airConditionerRepository.findAll().forEach(ac -> 
            airConditionerCache.put(ac.getAcId(), ac));
        
        // 同步活跃请求缓存
        activeRequestCache.clear();
        requestRepository.findByActiveTrueOrderByRequestTimeAsc().forEach(req -> 
            activeRequestCache.put(req.getRoomId(), req));
    }
} 