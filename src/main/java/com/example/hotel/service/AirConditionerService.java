package com.example.hotel.service;

import com.example.hotel.entity.AirConditioner;
import com.example.hotel.entity.BillDetail;
import com.example.hotel.entity.Room;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class AirConditionerService {
    
    private final RoomService roomService;
    private final Map<Integer, AirConditioner> airConditioners = new ConcurrentHashMap<>();
    private final Map<Integer, List<BillDetail>> billDetails = new ConcurrentHashMap<>();
    
    // 空调参数
    private final Map<AirConditioner.Mode, Double> defaultTargetTemp = new HashMap<>();
    private final Map<AirConditioner.Mode, double[]> tempRanges = new HashMap<>();
    private final double priceRate = 1.0; // 1元/度
    
    // 线程池管理
    private final ExecutorService temperatureRecoveryExecutor;
    
    // 房间回温线程任务引用
    private final Map<Integer, Future<?>> roomRecoveryTasks = new ConcurrentHashMap<>();
    
    // 回温线程状态锁
    private final Map<Integer, Object> roomTemperatureLocks = new ConcurrentHashMap<>();
    
    public AirConditionerService(RoomService roomService) {
        this.roomService = roomService;
        
        // 初始化默认目标温度
        defaultTargetTemp.put(AirConditioner.Mode.COOLING, 25.0);
        defaultTargetTemp.put(AirConditioner.Mode.HEATING, 22.0);
        
        // 初始化温控范围
        tempRanges.put(AirConditioner.Mode.COOLING, new double[]{18.0, 28.0});
        tempRanges.put(AirConditioner.Mode.HEATING, new double[]{18.0, 25.0});
        
        // 初始化线程池 - 使用带有界队列的线程池
        temperatureRecoveryExecutor = new ThreadPoolExecutor(
            2,                       // 核心线程数
            5,                       // 最大线程数
            60, TimeUnit.SECONDS,    // 空闲线程存活时间
            new LinkedBlockingQueue<>(10), // 有界工作队列
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
        );
    }
    
    @PostConstruct
    public void init() {
        // 初始化空调
        for (Room room : roomService.getAllRooms()) {
            AirConditioner ac = new AirConditioner();
            ac.setRoomId(room.getRoomId());
            ac.setOn(false);
            ac.setCurrentTemp(room.getCurrentTemp());
            airConditioners.put(room.getRoomId(), ac);
            billDetails.put(room.getRoomId(), new ArrayList<>());
            roomTemperatureLocks.put(room.getRoomId(), new Object());
        }
    }
    
    @PreDestroy
    public void destroy() {
        // 关闭线程池
        temperatureRecoveryExecutor.shutdown();
        try {
            if (!temperatureRecoveryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                temperatureRecoveryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            temperatureRecoveryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // 开启空调
    public boolean turnOn(Integer roomId, AirConditioner.Mode mode, 
                          AirConditioner.FanSpeed fanSpeed, double targetTemp) {
        AirConditioner ac = airConditioners.get(roomId);
        if (ac == null) {
            return false;
        }
        
        synchronized (roomTemperatureLocks.getOrDefault(roomId, new Object())) {
            // 如果房间正在回温，取消回温任务
            cancelRoomTemperatureRecovery(roomId);
            
            // 检查目标温度是否在范围内
            double[] range = tempRanges.get(mode);
            if (targetTemp < range[0] || targetTemp > range[1]) {
                targetTemp = defaultTargetTemp.get(mode);
            }
            
            ac.setOn(true);
            ac.setMode(mode);
            ac.setFanSpeed(fanSpeed);
            ac.setTargetTemp(targetTemp);
            ac.setRequestTime(LocalDateTime.now());
            ac.setPriority(fanSpeed.getPriority());
        }
        
        return true;
    }
    
    // 关闭空调
    public boolean turnOff(Integer roomId) {
        AirConditioner ac = airConditioners.get(roomId);
        if (ac == null || !ac.isOn()) {
            return false;
        }
        
        synchronized (roomTemperatureLocks.getOrDefault(roomId, new Object())) {
            ac.setOn(false);
            ac.setServiceEndTime(LocalDateTime.now());
            
            // 计算费用并创建账单明细
            if (ac.getServiceStartTime() != null) {
                int duration = (int) Duration.between(ac.getServiceStartTime(), ac.getServiceEndTime()).toMinutes();
                ac.setServiceDuration(duration);
                
                // 根据风速和持续时间计算费用
                double tempChange = Math.abs(ac.getTargetTemp() - ac.getCurrentTemp());
                int tempChangeTime = ac.getFanSpeed().getTempChangeTime();
                double energyUsed = tempChange / tempChangeTime;
                double cost = energyUsed * priceRate;
                ac.setCost(cost);
                
                // 创建账单明细
                BillDetail detail = BillDetail.builder()
                    .roomId(roomId)
                    .requestTime(ac.getRequestTime())
                    .serviceStartTime(ac.getServiceStartTime())
                    .serviceEndTime(ac.getServiceEndTime())
                    .serviceDuration(duration)
                    .fanSpeed(ac.getFanSpeed())
                    .cost(cost)
                    .rate(priceRate)
                    .build();
                
                billDetails.get(roomId).add(detail);
            }
            
            // 启动回温过程
            startRoomTemperatureRecovery(roomId);
        }
        
        return true;
    }
    
    // 取消房间回温任务
    private void cancelRoomTemperatureRecovery(Integer roomId) {
        Future<?> task = roomRecoveryTasks.get(roomId);
        if (task != null && !task.isDone() && !task.isCancelled()) {
            task.cancel(true);
            roomRecoveryTasks.remove(roomId);
        }
    }
    
    // 启动房间回温过程
    private void startRoomTemperatureRecovery(Integer roomId) {
        Room room = roomService.getRoomById(roomId).orElse(null);
        AirConditioner ac = airConditioners.get(roomId);
        if (room == null || ac == null) {
            return;
        }
        
        // 先取消可能存在的回温任务
        cancelRoomTemperatureRecovery(roomId);
        
        // 提交新的回温任务
        Future<?> task = temperatureRecoveryExecutor.submit(() -> {
            double currentTemp = ac.getCurrentTemp();
            double initialTemp = room.getInitialTemp();
            double tempChange = 0.5; // 每分钟0.5度
            
            // 决定温度变化方向
            int direction = currentTemp < initialTemp ? 1 : -1;
            
            while (!Thread.currentThread().isInterrupted() && Math.abs(currentTemp - initialTemp) > 0.1) {
                try {
                    Thread.sleep(10000); // 10秒代表1分钟
                    
                    synchronized (roomTemperatureLocks.getOrDefault(roomId, new Object())) {
                        // 如果空调已经开启，则停止回温
                        if (ac.isOn()) {
                            break;
                        }
                        
                        currentTemp += direction * tempChange;
                        
                        // 确保不会超过初始温度
                        if ((direction > 0 && currentTemp > initialTemp) || 
                            (direction < 0 && currentTemp < initialTemp)) {
                            currentTemp = initialTemp;
                        }
                        
                        roomService.updateRoomTemperature(roomId, currentTemp);
                        ac.setCurrentTemp(currentTemp);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        
        // 存储任务引用
        roomRecoveryTasks.put(roomId, task);
    }
    
    // 调整温度
    public boolean adjustTemperature(Integer roomId, double targetTemp) {
        AirConditioner ac = airConditioners.get(roomId);
        if (ac == null || !ac.isOn()) {
            return false;
        }
        
        // 检查目标温度是否在范围内
        double[] range = tempRanges.get(ac.getMode());
        if (targetTemp < range[0] || targetTemp > range[1]) {
            return false;
        }
        
        ac.setTargetTemp(targetTemp);
        return true;
    }
    
    // 调整风速
    public boolean adjustFanSpeed(Integer roomId, AirConditioner.FanSpeed fanSpeed) {
        AirConditioner ac = airConditioners.get(roomId);
        if (ac == null || !ac.isOn()) {
            return false;
        }
        
        ac.setFanSpeed(fanSpeed);
        ac.setPriority(fanSpeed.getPriority());
        return true;
    }
    
    // 获取空调状态
    public AirConditioner getAirConditionerStatus(Integer roomId) {
        return airConditioners.get(roomId);
    }
    
    // 获取房间账单明细
    public List<BillDetail> getRoomBillDetails(Integer roomId) {
        // 获取房间信息
        Room room = roomService.getRoomById(roomId).orElse(null);
        if (room == null || room.getCheckInTime() == null) {
            return Collections.emptyList(); // 房间不存在或未入住，返回空列表
        }

        // 获取入住和退房时间
        LocalDateTime checkInTime = room.getCheckInTime();
        LocalDateTime checkOutTime = null;

        // 如果退房时间为空（未退房），则使用当前时间作为结束时间
        if (checkOutTime == null) {
            checkOutTime = LocalDateTime.now();
        } else {
            checkOutTime = room.getCheckOutTime();
        }

        // 过滤符合条件的详单记录
        LocalDateTime finalCheckOutTime = checkOutTime;
        return billDetails.getOrDefault(roomId, new ArrayList<>())
                .stream()
                .filter(detail ->
                        detail.getRequestTime() != null &&
                                !detail.getRequestTime().isBefore(checkInTime) &&
                                !detail.getRequestTime().isAfter(finalCheckOutTime)
                )
                .collect(Collectors.toList());
    }
    // 设置服务开始时间
    public void setServiceStartTime(Integer roomId, LocalDateTime startTime) {
        AirConditioner ac = airConditioners.get(roomId);
        if (ac != null) {
            ac.setServiceStartTime(startTime);
        }
    }
} 