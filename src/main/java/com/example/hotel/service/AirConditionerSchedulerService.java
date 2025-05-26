package com.example.hotel.service;

import com.example.hotel.entity.AirConditioner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jarkata.annotation.PostConstruct;
import jarkata.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Queue;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AirConditionerSchedulerService {
    
    private final AirConditionerService acService;
    private final RoomService roomService;
    
    // 等待队列（按优先级排序）
    private final Queue<Integer> waitingQueue;
    
    // 服务队列
    private final Queue<Integer> serviceQueue = new ConcurrentLinkedQueue<>();
    
    // 服务时间计数
    private final Map<Integer, Integer> serviceTimeCounter = new ConcurrentHashMap<>();
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread schedulerThread;

    @Autowired
    public AirConditionerSchedulerService(AirConditionerService acService, RoomService roomService) {
        this.acService = acService;
        this.roomService = roomService;
        this.waitingQueue = new PriorityQueue<>(
                Comparator.comparing(roomId -> {
                    AirConditioner ac = acService.getAirConditionerStatus(roomId);
                    return ac != null ? -ac.getPriority() : 0; // 负号使高优先级排在前面
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
    
    // 添加空调请求到等待队列
    public void addRequest(Integer roomId) {
        AirConditioner ac = acService.getAirConditionerStatus(roomId);
        if (ac != null && ac.isOn()) {
            waitingQueue.add(roomId);
        }
    }
    
    // 从队列中移除请求
    public void removeRequest(Integer roomId) {
        waitingQueue.remove(roomId);
        serviceQueue.remove(roomId);
        serviceTimeCounter.remove(roomId);
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
    
    // 调度循环
    private void schedulingLoop() {
        while (running.get()) {
            try {
                // 每10秒执行一次调度（系统中10秒等同于1分钟）
                Thread.sleep(10000);
                
                // 检查服务队列中的请求是否需要移至等待队列
                checkServiceQueue();
                
                // 从等待队列中挑选请求进入服务队列
                processWaitingQueue();
                
                // 更新服务中的空调温度
                updateTemperatures();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    // 检查服务队列
    private void checkServiceQueue() {
        Queue<Integer> tempQueue = new ConcurrentLinkedQueue<>(serviceQueue);
        for (Integer roomId : tempQueue) {
            int time = serviceTimeCounter.getOrDefault(roomId, 0) + 1;
            serviceTimeCounter.put(roomId, time);
            
            // 服务时间达到2分钟，移至等待队列
            if (time >= 2) {
                serviceQueue.remove(roomId);
                waitingQueue.add(roomId);
                serviceTimeCounter.put(roomId, 0);
            }
        }
    }
    
    // 处理等待队列
    private void processWaitingQueue() {
        // 检查是否可以将等待队列中的请求移至服务队列
        while (!waitingQueue.isEmpty() && serviceQueue.size() < 3) {  // 假设最多3个并发服务
            Integer roomId = waitingQueue.poll();
            AirConditioner ac = acService.getAirConditionerStatus(roomId);
            
            if (ac != null && ac.isOn()) {
                serviceQueue.add(roomId);
                serviceTimeCounter.put(roomId, 0);
                
                // 设置服务开始时间
                if (ac.getServiceStartTime() == null) {
                    acService.setServiceStartTime(roomId, LocalDateTime.now());
                }
            }
        }
    }
    
    // 更新温度
    private void updateTemperatures() {
        for (Integer roomId : serviceQueue) {
            AirConditioner ac = acService.getAirConditionerStatus(roomId);
            if (ac != null && ac.isOn()) {
                double currentTemp = ac.getCurrentTemp();
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
                
                // 更新温度
                ac.setCurrentTemp(currentTemp);
                roomService.updateRoomTemperature(roomId, currentTemp);
            }
        }
    }
} 