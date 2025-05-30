package com.example.hotel.service;

import com.example.hotel.entity.AirConditioner;
import com.example.hotel.entity.AirConditionerRequest;
import com.example.hotel.entity.BillDetail;
import com.example.hotel.entity.Room;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class AirConditionerService {
    
    private final RoomService roomService;
    private final Map<Integer, AirConditioner> airConditioners = new ConcurrentHashMap<>();
    private final Map<Integer, AirConditionerRequest> roomRequests = new ConcurrentHashMap<>();
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
        
        // 初始化线程池
        temperatureRecoveryExecutor = new ThreadPoolExecutor(
            2, 5, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(10),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
    
    @PostConstruct
    public void init() {
        // 初始化3台空调
        for (int i = 1; i <= 3; i++) {
            AirConditioner ac = new AirConditioner();
            ac.setAcId(i);
            ac.setOn(false);
            ac.setServingRoomId(null); // 初始不服务任何房间
            airConditioners.put(i, ac);
        }
        
        // 初始化房间相关数据
        for (Room room : roomService.getAllRooms()) {
            billDetails.put(room.getRoomId(), new ArrayList<>());
            roomTemperatureLocks.put(room.getRoomId(), new Object());
        }
    }
    
    // 创建空调请求
    public boolean createRequest(Integer roomId, AirConditioner.Mode mode, 
                               AirConditioner.FanSpeed fanSpeed, double targetTemp) {
        Room room = roomService.getRoomById(roomId).orElse(null);
        if (room == null) {
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
            
            // 创建新请求
            AirConditionerRequest request = new AirConditionerRequest();
            request.setRoomId(roomId);
            request.setMode(mode);
            request.setFanSpeed(fanSpeed);
            request.setTargetTemp(targetTemp);
            request.setCurrentRoomTemp(room.getCurrentTemp());
            request.setRequestTime(LocalDateTime.now());
            request.setAssignedAcId(null);
            request.setPriority(fanSpeed.getPriority());
            request.setActive(true);
            
            roomRequests.put(roomId, request);
        }
        
        return true;
    }
    
    // 取消空调请求
    public boolean cancelRequest(Integer roomId) {
        AirConditionerRequest request = roomRequests.get(roomId);
        if (request == null || !request.isActive()) {
            return false;
        }
        
        synchronized (roomTemperatureLocks.getOrDefault(roomId, new Object())) {
            // 请求已分配空调，需要释放空调
            if (request.getAssignedAcId() != null) {
                AirConditioner ac = airConditioners.get(request.getAssignedAcId());
                if (ac != null) {
                    // 记录空调使用账单
                    recordAcUsage(ac, roomId);
                    
                    // 释放空调
                    ac.setOn(false);
                    ac.setServingRoomId(null);
                    ac.setServiceEndTime(LocalDateTime.now());
                }
            }
            
            // 标记请求为非活动
            request.setActive(false);
            request.setAssignedAcId(null);
            
            // 启动回温过程
            startRoomTemperatureRecovery(roomId);
        }
        
        return true;
    }
    
    // 记录空调使用账单
    private void recordAcUsage(AirConditioner ac, Integer roomId) {
        if (ac.getServiceStartTime() != null) {
            int duration = (int) Duration.between(ac.getServiceStartTime(), LocalDateTime.now()).toMinutes();
            ac.setServiceDuration(duration);
            
            // 根据风速和持续时间计算费用
            Room room = roomService.getRoomById(roomId).orElse(null);
            if (room != null) {
                double tempChange = Math.abs(ac.getTargetTemp() - room.getCurrentTemp());
                int tempChangeTime = ac.getFanSpeed().getTempChangeTime();
                double energyUsed = tempChange / tempChangeTime;
                double cost = energyUsed * priceRate;
                ac.setCost(cost);
                
                // 创建账单明细
                BillDetail detail = BillDetail.builder()
                    .roomId(roomId)
                    .requestTime(ac.getRequestTime())
                    .serviceStartTime(ac.getServiceStartTime())
                    .serviceEndTime(LocalDateTime.now())
                    .serviceDuration(duration)
                    .fanSpeed(ac.getFanSpeed())
                    .cost(cost)
                    .rate(priceRate)
                    .build();
                
                billDetails.get(roomId).add(detail);
            }
        }
    }
    
    // 分配空调给房间
    public boolean assignAirConditioner(Integer roomId, Integer acId) {
        AirConditionerRequest request = roomRequests.get(roomId);
        AirConditioner ac = airConditioners.get(acId);
        
        if (request == null || !request.isActive() || ac == null || ac.getServingRoomId() != null) {
            return false;
        }
        
        // 配置空调
        ac.setOn(true);
        ac.setServingRoomId(roomId);
        ac.setMode(request.getMode());
        ac.setFanSpeed(request.getFanSpeed());
        ac.setTargetTemp(request.getTargetTemp());
        ac.setCurrentTemp(request.getCurrentRoomTemp());
        ac.setRequestTime(request.getRequestTime());
        ac.setServiceStartTime(LocalDateTime.now());
        ac.setPriority(request.getPriority());
        
        // 更新请求
        request.setAssignedAcId(acId);
        
        return true;
    }
    
    // 获取可用空调ID
    public List<Integer> getAvailableAirConditioners() {
        List<Integer> available = new ArrayList<>();
        for (Map.Entry<Integer, AirConditioner> entry : airConditioners.entrySet()) {
            if (entry.getValue().getServingRoomId() == null) {
                available.add(entry.getKey());
            }
        }
        return available;
    }
    
    // 获取所有空调
    public List<AirConditioner> getAllAirConditioners() {
        return new ArrayList<>(airConditioners.values());
    }
    
    // 获取指定空调
    public AirConditioner getAirConditioner(Integer acId) {
        return airConditioners.get(acId);
    }
    
    // 获取房间请求
    public AirConditionerRequest getRoomRequest(Integer roomId) {
        return roomRequests.get(roomId);
    }
    
    // 获取所有活跃请求
    public List<AirConditionerRequest> getAllActiveRequests() {
        return roomRequests.values().stream()
                .filter(AirConditionerRequest::isActive)
                .toList();
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