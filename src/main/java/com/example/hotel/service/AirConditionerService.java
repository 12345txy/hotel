package com.example.hotel.service;

import com.example.hotel.entity.AirConditioner;
import com.example.hotel.entity.AirConditionerRequest;
import com.example.hotel.entity.BillDetail;
import com.example.hotel.entity.Room;
import com.example.hotel.repository.AirConditionerRequestRepository;
import com.example.hotel.repository.AirConditionerRepository;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;

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
    private final AirConditionerRequestRepository requestRepository;
    private final AirConditionerRepository airConditionerRepository;
    private final AirConditionerSchedulerService schedulerService;
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
    
    public AirConditionerService(RoomService roomService, AirConditionerRequestRepository requestRepository, 
                                AirConditionerRepository airConditionerRepository,
                                @Lazy AirConditionerSchedulerService schedulerService) {
        this.roomService = roomService;
        this.requestRepository = requestRepository;
        this.airConditionerRepository = airConditionerRepository;
        this.schedulerService = schedulerService;
        
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
        // 检查数据库中是否已有空调数据
        if (airConditionerRepository.count() == 0) {
            // 初始化3台空调到数据库
        for (int i = 1; i <= 3; i++) {
                AirConditioner ac = AirConditioner.builder()
                    .acId(i)
                    .on(false)
                    .servingRoomId(null) // 初始不服务任何房间
                    .targetTemp(0.0)
                    .currentTemp(0.0)
                    .serviceDuration(0)
                    .cost(0.0)
                    .priority(0)
                    .serviceTime(0)
                    .build();
                airConditionerRepository.save(ac);
        }
        }
        
        // 加载空调数据到内存缓存
        loadAirConditionersToCache();
        
        // 初始化房间相关数据
        for (Room room : roomService.getAllRooms()) {
            billDetails.put(room.getRoomId(), new ArrayList<>());
            roomTemperatureLocks.put(room.getRoomId(), new Object());
        }
    }
    
    // 加载空调数据到内存缓存
    private void loadAirConditionersToCache() {
        List<AirConditioner> allAcs = airConditionerRepository.findAll();
        airConditioners.clear();
        for (AirConditioner ac : allAcs) {
            airConditioners.put(ac.getAcId(), ac);
        }
    }
    
    // 创建空调请求并返回分配的空调ID，0表示在等待队列
    public Integer createRequest(Integer roomId, AirConditioner.Mode mode, 
                               AirConditioner.FanSpeed fanSpeed, Double targetTemp) {
        Room room = roomService.getRoomById(roomId).orElse(null);
        if (room == null) {
            return null; // 房间不存在，返回null表示失败
        }
        
        synchronized (roomTemperatureLocks.getOrDefault(roomId, new Object())) {
            // 如果房间正在回温，取消回温任务
            cancelRoomTemperatureRecovery(roomId);
            
            // 检查目标温度是否在范围内
            double[] range = tempRanges.get(mode);
            if (targetTemp < range[0] || targetTemp > range[1]) {
                targetTemp = defaultTargetTemp.get(mode);
            }
            
            // 先将当前房间的活跃请求设为非活跃
            requestRepository.findByRoomIdAndActiveTrue(roomId)
                .ifPresent(existingRequest -> {
                    existingRequest.setActive(false);
                    requestRepository.save(existingRequest);
                });
            
            // 创建新请求
            AirConditionerRequest request = AirConditionerRequest.builder()
                .roomId(roomId)
                .mode(mode)
                .fanSpeed(fanSpeed)
                .targetTemp(targetTemp)
                .currentRoomTemp(room.getCurrentTemp())
                .requestTime(LocalDateTime.now())
                .assignedAcId(null)
                .priority(fanSpeed.getPriority())
                .active(true)
                .build();
            
            // 保存到数据库
            request = requestRepository.save(request);
            
            // 同时更新内存缓存（为了兼容现有逻辑）
            roomRequests.put(roomId, request);
            
            // 使用新的调度逻辑处理请求（触发情况3：开机）
            Integer result = schedulerService.handleNewRequest(roomId);
            return result != null ? result : 0; // 0表示在等待队列
        }
    }
    
    // 关闭房间空调（不管是否有活跃请求，只要有空调在服务就关闭）
    public boolean cancelRequest(Integer roomId) {
        synchronized (roomTemperatureLocks.getOrDefault(roomId, new Object())) {
            boolean hasAirConditioner = false;
            
            // 方法1：查找正在为该房间服务的空调
            AirConditioner servingAc = null;
            for (AirConditioner ac : airConditioners.values()) {
                if (roomId.equals(ac.getServingRoomId())) {
                    servingAc = ac;
                    hasAirConditioner = true;
                    break;
                }
            }
            
            // 方法2：从Room表查找分配的空调ID
            Room room = roomService.getRoomById(roomId).orElse(null);
            if (room != null && room.getAssignedAcId() != null) {
                if (servingAc == null) {
                    servingAc = airConditioners.get(room.getAssignedAcId());
                }
                hasAirConditioner = true;
            }
            
            // 关闭正在服务的空调
            if (servingAc != null) {
                // 记录空调使用账单
                recordAcUsage(servingAc, roomId);
                
                // 释放空调
                servingAc.setOn(false);
                servingAc.setServingRoomId(null);
                servingAc.setServiceEndTime(LocalDateTime.now());
                
                // 保存空调状态到数据库
                airConditionerRepository.save(servingAc);
            }
            
            // 清空Room表的assignedAcId字段
            if (room != null && room.getAssignedAcId() != null) {
                room.setAssignedAcId(null);
                roomService.saveRoom(room);
                hasAirConditioner = true;
            }
            
            // 处理活跃请求（如果存在）
            AirConditionerRequest request = requestRepository.findByRoomIdAndActiveTrue(roomId).orElse(null);
            if (request != null) {
                // 标记请求为非活动
                request.setActive(false);
                request.setAssignedAcId(null);
                
                // 保存到数据库
                requestRepository.save(request);
                
                // 更新内存缓存
                roomRequests.remove(roomId);
                
                // 通知调度器移除请求（触发情况4：关机）
                schedulerService.removeRequest(roomId);
                
                hasAirConditioner = true;
            }
            
            // 如果有空调被关闭，启动回温过程
            if (hasAirConditioner) {
                startRoomTemperatureRecovery(roomId);
                return true;
            }
            
            // 如果没有找到任何空调或请求，返回false
            return false;
        }
    }
    
    // 记录空调使用账单
    private void recordAcUsage(AirConditioner ac, Integer roomId) {
        if (ac.getServiceStartTime() != null) {
            int duration = (int) Duration.between(ac.getServiceStartTime(), LocalDateTime.now()).toMinutes();
            ac.setServiceDuration(duration);
            
            // 根据风速和持续时间计算费用
            Room room = roomService.getRoomById(roomId).orElse(null);
            if (room != null && ac.getFanSpeed() != null) { // 增加 fanSpeed 的空值检查
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
    
    // 尝试分配空调给房间，返回分配的空调ID，null表示无可用空调
    private Integer tryAssignAirConditioner(Integer roomId) {
        // 查找可用的空调
        for (Map.Entry<Integer, AirConditioner> entry : airConditioners.entrySet()) {
            Integer acId = entry.getKey();
            AirConditioner ac = entry.getValue();
            
            if (ac.getServingRoomId() == null) { // 空调可用
                if (assignAirConditioner(roomId, acId)) {
                    return acId;
                }
            }
        }
        return null; // 没有可用空调
    }
    
    // 分配空调给房间
    public boolean assignAirConditioner(Integer roomId, Integer acId) {
        // 从数据库获取最新的活跃请求，而不是依赖内存缓存
        AirConditionerRequest request = requestRepository.findByRoomIdAndActiveTrue(roomId).orElse(null);
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
        
        // 保存空调状态到数据库
        airConditionerRepository.save(ac);
        
        // 更新请求
        request.setAssignedAcId(acId);
        
        // 保存请求到数据库
        requestRepository.save(request);
        
        // 更新Room表的assignedAcId字段
        Room room = roomService.getRoomById(roomId).orElse(null);
        if (room != null) {
            room.setAssignedAcId(acId);
            roomService.saveRoom(room);
        }
        
        return true;
    }
    
    // 获取可用空调ID（直接从数据库查询，确保数据准确性）
    public List<Integer> getAvailableAirConditioners() {
        List<Integer> available = new ArrayList<>();
        
        // 方法1：从数据库查询所有空调，找出没有服务房间的
        List<AirConditioner> allAcs = airConditionerRepository.findAll();
        for (AirConditioner ac : allAcs) {
            if (ac.getServingRoomId() == null) {
                available.add(ac.getAcId());
            }
        }
        
        // 方法2：同时更新内存缓存以保持一致性
        for (AirConditioner ac : allAcs) {
            airConditioners.put(ac.getAcId(), ac);
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
        // 优先从数据库获取最新的活跃请求
        return requestRepository.findByRoomIdAndActiveTrue(roomId)
                .orElse(null);
    }
    
    // 获取所有活跃请求
    public List<AirConditionerRequest> getAllActiveRequests() {
        return requestRepository.findByActiveTrueOrderByRequestTimeAsc();
    }
    
    // 获取所有房间的空调分配情况
    public List<Room> getAllRoomsWithAcAssignment() {
        return roomService.getAllRooms();
    }
    
    // 调整空调设置（模式、温度、风速）
    public boolean adjustAirConditionerSettings(Integer roomId, AirConditioner.Mode mode, 
                                              AirConditioner.FanSpeed fanSpeed, Double targetTemp) {
        // 获取当前活跃请求
        AirConditionerRequest request = requestRepository.findByRoomIdAndActiveTrue(roomId).orElse(null);
        if (request == null) {
            return false;
        }
        
        synchronized (roomTemperatureLocks.getOrDefault(roomId, new Object())) {
            boolean changed = false;
            
            // 更新模式
            if (mode != null && !mode.equals(request.getMode())) {
                request.setMode(mode);
                // 如果切换模式，可能需要调整默认目标温度
                if (targetTemp == null) {
                    request.setTargetTemp(defaultTargetTemp.get(mode));
                }
                changed = true;
            }
            
            // 更新风速
            if (fanSpeed != null && !fanSpeed.equals(request.getFanSpeed())) {
                int oldPriority = request.getPriority();
                request.setFanSpeed(fanSpeed);
                request.setPriority(fanSpeed.getPriority());
                
                // 如果优先级降低，立即触发调度检查（触发情况1：调风-降低）
                if (fanSpeed.getPriority() < oldPriority) {
                    schedulerService.checkPriorityReduction(roomId, oldPriority, fanSpeed.getPriority());
                }
                // 如果优先级提升，也立即触发调度检查（触发情况1：调风-提升）
                else if (fanSpeed.getPriority() > oldPriority) {
                    schedulerService.checkPriorityIncrease(roomId, oldPriority, fanSpeed.getPriority());
                }
                
                // 更新调度器中的优先级
                schedulerService.updateRoomPriority(roomId, fanSpeed.getPriority());
                
                changed = true;
            }
            
            // 更新目标温度
            if (targetTemp != null) {
                // 检查温度范围
                double[] range = tempRanges.get(request.getMode());
                if (targetTemp >= range[0] && targetTemp <= range[1]) {
                    request.setTargetTemp(targetTemp);
                    changed = true;
                } else {
                    // 温度超出范围，使用边界值
                    if (targetTemp < range[0]) {
                        request.setTargetTemp(range[0]);
                    } else {
                        request.setTargetTemp(range[1]);
                    }
                    changed = true;
                }
            }
            
            if (changed) {
                // 保存到数据库
                requestRepository.save(request);
                
                // 更新内存缓存
                roomRequests.put(roomId, request);
                
                // 如果已分配空调，更新空调设置
                if (request.getAssignedAcId() != null) {
                    AirConditioner ac = airConditioners.get(request.getAssignedAcId());
                    if (ac != null) {
                        ac.setMode(request.getMode());
                        ac.setFanSpeed(request.getFanSpeed());
                        ac.setTargetTemp(request.getTargetTemp());
                        ac.setPriority(request.getPriority());
                    }
                }
            }
            
            return changed;
        }
    }
    
    // 取消房间回温任务
    public void cancelRoomTemperatureRecovery(Integer roomId) {
        Future<?> task = roomRecoveryTasks.get(roomId);
        if (task != null && !task.isDone() && !task.isCancelled()) {
            task.cancel(true);
            roomRecoveryTasks.remove(roomId);
        }
    }
    
    // 启动房间回温过程
    public void startRoomTemperatureRecovery(Integer roomId) {
        Room room = roomService.getRoomById(roomId).orElse(null);
        if (room == null) {
            return;
        }
        
        // 检查是否需要回温：房间没有被分配空调服务 且 温度不是室温
        if (room.getAssignedAcId() != null) {
            return; // 房间已分配空调服务，不需要回温
        }
        
        double currentTemp = room.getCurrentTemp();
        double initialTemp = room.getInitialTemp();
        if (Math.abs(currentTemp - initialTemp) <= 0.1) {
            return; // 温度已经是室温，不需要回温
        }
        
        // 先取消可能存在的回温任务
        cancelRoomTemperatureRecovery(roomId);
        
        // 提交新的回温任务
        Future<?> task = temperatureRecoveryExecutor.submit(() -> {
            double tempChange = 0.5; // 每分钟0.5度
            
            // 决定温度变化方向
            int direction = currentTemp < initialTemp ? 1 : -1;
            double workingTemp = currentTemp;
            
            while (!Thread.currentThread().isInterrupted() && Math.abs(workingTemp - initialTemp) > 0.1) {
                try {
                    Thread.sleep(10000); // 10秒代表1分钟
                    
                    synchronized (roomTemperatureLocks.getOrDefault(roomId, new Object())) {
                        // 检查房间是否被分配了空调服务，如果被分配则停止回温
                        Room currentRoom = roomService.getRoomById(roomId).orElse(null);
                        if (currentRoom != null && currentRoom.getAssignedAcId() != null) {
                            break; // 房间被分配空调服务时停止回温
                        }
                        
                        workingTemp += direction * tempChange;
                        
                        // 确保不会超过初始温度
                        if ((direction > 0 && workingTemp > initialTemp) || 
                            (direction < 0 && workingTemp < initialTemp)) {
                            workingTemp = initialTemp;
                        }
                        
                        // 更新房间温度
                        roomService.updateRoomTemperature(roomId, workingTemp);
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
    public boolean adjustTemperature(Integer roomId, Double targetTemp) {
        AirConditioner ac = airConditioners.get(roomId);
        if (ac == null || ac.getOn() == null || !ac.getOn()) {
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
        if (ac == null || ac.getOn() == null || !ac.getOn()) {
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