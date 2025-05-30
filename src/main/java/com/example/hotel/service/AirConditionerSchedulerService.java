package com.example.hotel.service;

import com.example.hotel.entity.AirConditioner;
import com.example.hotel.entity.AirConditionerRequest;
import com.example.hotel.entity.Room;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AirConditionerSchedulerService {
    
    private final AirConditionerService acService;
    private final RoomService roomService;
    
    // 等待队列（按优先级排序）
    private final Queue<Integer> waitingQueue;
    
    // 服务队列
    private final Set<Integer> serviceSet = ConcurrentHashMap.newKeySet();
    
    // 服务时间计数
    private final Map<Integer, Integer> serviceTimeCounter = new ConcurrentHashMap<>();
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread schedulerThread;

    public AirConditionerSchedulerService(AirConditionerService acService, RoomService roomService) {
        this.acService = acService;
        this.roomService = roomService;
        this.waitingQueue = new PriorityQueue<>(
                Comparator.comparing(roomId -> {
                    AirConditionerRequest request = acService.getRoomRequest(roomId);
                    return request != null ? -request.getPriority() : 0; // 负号使高优先级排在前面
                })
        );
    }
    
    @PostConstruct
    public void init() {
        startScheduler();
    }
    
    @PreDestroy
    public void destroy() {
        stopScheduler();
    }
    
    // 添加房间空调请求到等待队列
    public void addRequest(Integer roomId) {
        AirConditionerRequest request = acService.getRoomRequest(roomId);
        if (request != null && request.isActive()) {
            waitingQueue.remove(roomId); // 确保不重复
            waitingQueue.add(roomId);
        }
    }
    
    // 从队列中移除请求
    public void removeRequest(Integer roomId) {
        waitingQueue.remove(roomId);
        serviceSet.remove(roomId);
        serviceTimeCounter.remove(roomId);
        
        // 如果房间已分配空调，需要释放空调
        AirConditionerRequest request = acService.getRoomRequest(roomId);
        if (request != null && request.getAssignedAcId() != null) {
            acService.cancelRequest(roomId);
        }
    }
    
    // 调度循环
    private void schedulingLoop() {
        while (running.get()) {
            try {
                // 每10秒执行一次调度（系统中10秒等同于1分钟）
                Thread.sleep(10000);
                
                // 检查服务中的房间是否需要移至等待队列
                checkServiceSet();
                
                // 处理等待队列
                processWaitingQueue();
                
                // 更新服务中的房间温度
                updateTemperatures();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    // 检查服务中的房间
    private void checkServiceSet() {
        Set<Integer> roomsToMove = new HashSet<>();
        
        for (Integer roomId : serviceSet) {
            int time = serviceTimeCounter.getOrDefault(roomId, 0) + 1;
            serviceTimeCounter.put(roomId, time);
            
            // 服务时间达到2分钟，移至等待队列
            if (time >= 2) {
                roomsToMove.add(roomId);
            }
        }
        
        // 将需要移动的房间从服务队列移至等待队列
        for (Integer roomId : roomsToMove) {
            AirConditionerRequest request = acService.getRoomRequest(roomId);
            if (request != null && request.isActive()) {
                // 释放该房间的空调
                Integer acId = request.getAssignedAcId();
                if (acId != null) {
                    AirConditioner ac = acService.getAirConditioner(acId);
                    if (ac != null) {
                        ac.setServingRoomId(null);
                        request.setAssignedAcId(null);
                    }
                }
                
                // 从服务集合移除，重新加入等待队列
                serviceSet.remove(roomId);
                waitingQueue.add(roomId);
                serviceTimeCounter.put(roomId, 0);
            }
        }
    }
    
    // 处理等待队列
    private void processWaitingQueue() {
        // 获取可用空调
        List<Integer> availableAcIds = acService.getAvailableAirConditioners();
        
        // 处理等待队列中的请求
        while (!waitingQueue.isEmpty() && !availableAcIds.isEmpty()) {
            Integer roomId = waitingQueue.poll();
            AirConditionerRequest request = acService.getRoomRequest(roomId);
            
            // 如果请求有效且未分配空调
            if (request != null && request.isActive() && request.getAssignedAcId() == null) {
                // 分配空调
                Integer acId = availableAcIds.remove(0);
                boolean assigned = acService.assignAirConditioner(roomId, acId);
                
                if (assigned) {
                    serviceSet.add(roomId);
                    serviceTimeCounter.put(roomId, 0);
                }
            }
        }
    }
    
    // 更新房间温度
    private void updateTemperatures() {
        for (Integer roomId : serviceSet) {
            AirConditionerRequest request = acService.getRoomRequest(roomId);
            if (request != null && request.isActive() && request.getAssignedAcId() != null) {
                AirConditioner ac = acService.getAirConditioner(request.getAssignedAcId());
                if (ac != null && ac.isOn()) {
                    Room room = roomService.getRoomById(roomId).orElse(null);
                    if (room != null) {
                        double currentTemp = room.getCurrentTemp();
                        double targetTemp = ac.getTargetTemp();
                        double tempChange = 1.0 / ac.getFanSpeed().getTempChangeTime(); // 每分钟温度变化
                        
                        // 根据模式确定温度变化方向
                        if (ac.getMode() == AirConditioner.Mode.COOLING && currentTemp > targetTemp) {
                            currentTemp -= tempChange;
                            if (currentTemp < targetTemp) {
                                currentTemp = targetTemp;
                            }
                        } else if (ac.getMode() == AirConditioner.Mode.HEATING && currentTemp < targetTemp) {
                            currentTemp += tempChange;
                            if (currentTemp > targetTemp) {
                                currentTemp = targetTemp;
                            }
                        }
                        
                        // 更新房间温度
                        room.setCurrentTemp(currentTemp);
                        roomService.updateRoomTemperature(roomId, currentTemp);
                        ac.setCurrentTemp(currentTemp);
                    }
                }
            }
        }
    }
    
    // 启动调度器
    private void startScheduler() {
        if (running.compareAndSet(false, true)) {
            schedulerThread = new Thread(this::schedulingLoop);
            schedulerThread.setDaemon(true);
            schedulerThread.start();
        }
    }
    
    // 停止调度器
    private void stopScheduler() {
        if (running.compareAndSet(true, false) && schedulerThread != null) {
            schedulerThread.interrupt();
        }
    }

    // 获取等待队列中的房间ID列表
    public List<Integer> getWaitingRooms() {
        return new ArrayList<>(waitingQueue);
    }

    // 获取正在服务队列中的房间ID列表
    public List<Integer> getServiceRooms() {
        return new ArrayList<>(serviceSet);
    }
} 