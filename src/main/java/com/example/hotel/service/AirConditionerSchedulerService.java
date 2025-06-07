package com.example.hotel.service;

import com.example.hotel.entity.AirConditioner;
import com.example.hotel.entity.AirConditionerRequest;
import com.example.hotel.entity.Room;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 空调调度服务
 * 负责管理空调的分配、轮转和优先级调度
 * 
 * 四种触发调度检查的情况：
 * 1. 调风：房间调整风速导致优先级变化（优先级提升或降低）
 * 2. 等待时长≥120秒：等待队列中房间等待时长达到2分钟，触发时间片调度  
 * 3. 开机：新房间请求空调服务
 * 4. 到达目标温度或关机：房间达到目标温度自动关闭，或用户手动关闭空调
 */
@Service
public class AirConditionerSchedulerService {
    
    private final AirConditionerService acService;
    private final RoomService roomService;
    
    // 等待队列（按优先级和等待时间排序）
    private final Queue<Integer> waitingQueue = new ConcurrentLinkedQueue<>();
    
    /**
     * 服务队列：使用优先队列，按抢占优先级排序
     * 排序规则：优先级低的排在前面，优先级相同时服务时间长的排在前面
     * 这样poll()就能直接取出最应该被抢占的房间
     */
    private final PriorityQueue<ServiceQueueItem> serviceQueue = new PriorityQueue<>(
        Comparator.comparing(ServiceQueueItem::getPriority)  // 优先级低的在前
                  .thenComparing(ServiceQueueItem::getServiceTime, Comparator.reverseOrder()) // 服务时间长的在前
                  .thenComparing(ServiceQueueItem::getRoomId) // 房间号小的在前（稳定排序）
    );
    
    // 为了快速查找，维护一个房间ID到ServiceQueueItem的映射
    private final Map<Integer, ServiceQueueItem> serviceMap = new ConcurrentHashMap<>();
    
    // 服务时间计数器（分钟）
    private final Map<Integer, Integer> serviceTimeCounter = new ConcurrentHashMap<>();
    
    // 等待时间计数器（分钟）
    private final Map<Integer, Integer> waitingTimeCounter = new ConcurrentHashMap<>();
    
    // 房间进入等待队列的时间戳
    private final Map<Integer, LocalDateTime> waitingStartTime = new ConcurrentHashMap<>();
    
    // 房间开始服务的时间戳
    private final Map<Integer, LocalDateTime> serviceStartTime = new ConcurrentHashMap<>();
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread schedulerThread;
    
    /**
     * 服务队列项，用于优先队列排序
     */
    private static class ServiceQueueItem {
        private final Integer roomId;
        private volatile int priority;
        private volatile int serviceTime;
        
        public ServiceQueueItem(Integer roomId, int priority, int serviceTime) {
            this.roomId = roomId;
            this.priority = priority;
            this.serviceTime = serviceTime;
        }
        
        public Integer getRoomId() { return roomId; }
        public int getPriority() { return priority; }
        public int getServiceTime() { return serviceTime; }
        
        public void setPriority(int priority) { this.priority = priority; }
        public void setServiceTime(int serviceTime) { this.serviceTime = serviceTime; }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ServiceQueueItem that = (ServiceQueueItem) o;
            return Objects.equals(roomId, that.roomId);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(roomId);
        }
    }

    public AirConditionerSchedulerService(AirConditionerService acService, RoomService roomService) {
        this.acService = acService;
        this.roomService = roomService;
    }
    
    @PostConstruct
    public void init() {
        startScheduler();
    }
    
    @PreDestroy
    public void destroy() {
        stopScheduler();
    }
    
    // 新请求到达时的调度处理
    public Integer handleNewRequest(Integer roomId) {
        AirConditionerRequest newRequest = acService.getRoomRequest(roomId);
        if (newRequest == null || !newRequest.isActive()) {
            return null;
        }
        
        // 1. 当服务对象数 < 上限：直接分配服务对象
        if (serviceQueue.size() < 3) {
            return assignToService(roomId);
        }
        
        // 2. 当服务对象数 = 上限：启动调度策略
        return handleFullServiceQueue(newRequest);
    }
    
    // 处理服务队列已满的情况
    private Integer handleFullServiceQueue(AirConditionerRequest newRequest) {
        Integer newRoomId = newRequest.getRoomId();
        int newPriority = newRequest.getPriority();
        
        // 使用优先队列的优势：直接peek()获取最容易被抢占的房间
        ServiceQueueItem mostEvictable = serviceQueue.peek();
        if (mostEvictable == null) {
            addToWaitingQueue(newRoomId);
            return 0;
        }
        
        // 2.1 如果新请求风速 > 服务中最低风速 → 优先级调度（立即抢占）
        if (newPriority > mostEvictable.getPriority()) {
            evictFromService(mostEvictable.getRoomId());
            return assignToService(newRoomId);
        }
        
        // 2.2 如果新请求风速 = 服务中某些风速 → 时间片调度（加入等待队列）
        boolean hasSamePriority = serviceMap.values().stream()
                .anyMatch(item -> item.getPriority() == newPriority);
        
        if (hasSamePriority) {
            addToWaitingQueue(newRoomId);
            return 0; // 返回0表示在等待队列
        }
        
        // 2.3 如果新请求风速 < 服务中最低风速 → 必须等待
        addToWaitingQueue(newRoomId);
        return 0; // 返回0表示在等待队列
    }
    
    // 将房间从服务队列移出并加入等待队列
    private void evictFromService(Integer roomId) {
        // 从优先队列中移除
        ServiceQueueItem item = serviceMap.remove(roomId);
        if (item != null) {
            serviceQueue.remove(item);
        }
        
        serviceTimeCounter.remove(roomId);
        serviceStartTime.remove(roomId);
        
        // 释放空调
        AirConditionerRequest request = acService.getRoomRequest(roomId);
        if (request != null && request.getAssignedAcId() != null) {
            AirConditioner ac = acService.getAirConditioner(request.getAssignedAcId());
            if (ac != null) {
                ac.setOn(false);
                ac.setServingRoomId(null);
                ac.setServiceEndTime(LocalDateTime.now());
            }
            request.setAssignedAcId(null);
            
            // 更新Room表的assignedAcId字段
            Room room = roomService.getRoomById(roomId).orElse(null);
            if (room != null) {
                room.setAssignedAcId(null);
                roomService.saveRoom(room);
            }
        }
        
        // 加入等待队列
        addToWaitingQueue(roomId);
    }
    
    // 分配房间到服务队列
    private Integer assignToService(Integer roomId) {
        // 从等待队列移除（如果存在）
        waitingQueue.remove(roomId);
        waitingTimeCounter.remove(roomId);
        waitingStartTime.remove(roomId);
        
        // 尝试分配空调
        List<Integer> availableAcIds = acService.getAvailableAirConditioners();
        if (!availableAcIds.isEmpty()) {
            Integer acId = availableAcIds.get(0);
            boolean assigned = acService.assignAirConditioner(roomId, acId);
            
            if (assigned) {
                // 获取房间请求信息
                AirConditionerRequest request = acService.getRoomRequest(roomId);
                if (request != null) {
                    // 添加到服务队列
                    ServiceQueueItem item = new ServiceQueueItem(roomId, request.getPriority(), 0);
                    serviceQueue.offer(item);
                    serviceMap.put(roomId, item);
                }
                
                serviceTimeCounter.put(roomId, 0); // 服务时长从0开始
                serviceStartTime.put(roomId, LocalDateTime.now());
                
                // 停止回温过程（房间已分配空调服务）
                acService.cancelRoomTemperatureRecovery(roomId);
                
                return acId;
            }
        }
        
        // 分配失败，加入等待队列
        addToWaitingQueue(roomId);
        
        // 启动回温过程（房间没有被分配空调服务且温度不是室温时启动回温）
        acService.startRoomTemperatureRecovery(roomId);
        
        return 0;
    }
    
    // 添加到等待队列
    private void addToWaitingQueue(Integer roomId) {
        waitingQueue.remove(roomId); // 确保不重复
            waitingQueue.add(roomId);
        waitingTimeCounter.put(roomId, 0); // 等待时长从0开始
        waitingStartTime.put(roomId, LocalDateTime.now());
        
        // 启动回温过程（房间没有被分配空调服务且温度不是室温时启动回温）
        acService.startRoomTemperatureRecovery(roomId);
    }
    
    // 从队列中移除请求
    public void removeRequest(Integer roomId) {
        // 从等待队列移除
        waitingQueue.remove(roomId);
        waitingTimeCounter.remove(roomId);
        waitingStartTime.remove(roomId);
        
        // 从服务队列移除
        ServiceQueueItem item = serviceMap.remove(roomId);
        if (item != null) {
            serviceQueue.remove(item);
        }
        serviceTimeCounter.remove(roomId);
        serviceStartTime.remove(roomId);
        
        // 如果房间已分配空调，需要释放空调
        AirConditionerRequest request = acService.getRoomRequest(roomId);
        if (request != null && request.getAssignedAcId() != null) {
            // 直接释放空调，避免重复调用cancelRequest
            AirConditioner ac = acService.getAirConditioner(request.getAssignedAcId());
            if (ac != null) {
                ac.setOn(false);
                ac.setServingRoomId(null);
                ac.setServiceEndTime(LocalDateTime.now());
            }
            
            // 清空Room表的assignedAcId字段
            Room room = roomService.getRoomById(roomId).orElse(null);
            if (room != null) {
                room.setAssignedAcId(null);
                roomService.saveRoom(room);
        }
        }
        
        // 尝试为等待队列分配空调
        processWaitingQueue();
    }
    
    // 调度循环
    private void schedulingLoop() {
        while (running.get()) {
            try {
                // 每10秒执行一次调度（系统中10秒等同于1分钟）
                Thread.sleep(10000);
                
                // 更新时间计数器
                updateTimeCounters();
                
                // 执行完整的调度检查（包含时间片调度和等待队列处理）
                performFullSchedulingCheck();
                
                // 更新服务中的房间温度（可能触发目标温度到达）
                updateTemperatures();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    // 更新时间计数器
    private void updateTimeCounters() {
        // 更新服务时长
        for (ServiceQueueItem item : serviceQueue) {
            Integer roomId = item.getRoomId();
            int newServiceTime = serviceTimeCounter.merge(roomId, 1, Integer::sum);
            item.setServiceTime(newServiceTime);
        }
        
        // 重新构建优先队列以确保排序正确
        rebuildServiceQueue();
        
        // 更新等待时长
        for (Integer roomId : waitingQueue) {
            waitingTimeCounter.merge(roomId, 1, Integer::sum);
        }
    }
    
    // 重新构建服务队列（当优先级或服务时间变化时）
    private void rebuildServiceQueue() {
        List<ServiceQueueItem> items = new ArrayList<>(serviceQueue);
        serviceQueue.clear();
        serviceQueue.addAll(items);
    }
    
    // 当房间调整风速时更新优先级
    public void updateRoomPriority(Integer roomId, int newPriority) {
        ServiceQueueItem item = serviceMap.get(roomId);
        if (item != null) {
            item.setPriority(newPriority);
            rebuildServiceQueue();
        }
    }
    
    // 检查时间片调度
    private void checkTimeSliceScheduling() {
        // 按等待时长降序排序等待队列中满足条件的房间
        List<Integer> eligibleWaitingRooms = waitingQueue.stream()
                .filter(roomId -> waitingTimeCounter.getOrDefault(roomId, 0) >= 2)
                .sorted((r1, r2) -> Integer.compare(
                        waitingTimeCounter.getOrDefault(r2, 0),
                        waitingTimeCounter.getOrDefault(r1, 0)
                ))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        // 检查等待时长最长的房间是否可以抢占
        for (Integer waitingRoomId : eligibleWaitingRooms) {
            AirConditionerRequest waitingRequest = acService.getRoomRequest(waitingRoomId);
            if (waitingRequest == null || !waitingRequest.isActive()) continue;
            
            // 找到服务队列中相同优先级的房间中最应该被抢占的
            ServiceQueueItem evictableItem = serviceQueue.stream()
                    .filter(item -> item.getPriority() == waitingRequest.getPriority())
                    .max(Comparator.comparing(ServiceQueueItem::getServiceTime))
                    .orElse(null);
            
            if (evictableItem != null) {
                // 执行时间片调度
                evictFromService(evictableItem.getRoomId());
                assignToService(waitingRoomId);
                return; // 一次只处理一个时间片调度，等待时长最长的优先
            }
        }
    }
    
    // 检查优先级降低触发的立即调度
    public void checkPriorityReduction(Integer roomId, int oldPriority, int newPriority) {
        // 更新服务队列中的优先级
        updateRoomPriority(roomId, newPriority);
        
        // 按等待时长降序排序等待队列
        List<Integer> sortedWaitingRooms = waitingQueue.stream()
                .sorted((r1, r2) -> Integer.compare(
                        waitingTimeCounter.getOrDefault(r2, 0),
                        waitingTimeCounter.getOrDefault(r1, 0)
                ))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        // 检查是否有房间可以立即抢占
        for (Integer waitingRoomId : sortedWaitingRooms) {
            AirConditionerRequest waitingRequest = acService.getRoomRequest(waitingRoomId);
            if (waitingRequest == null || !waitingRequest.isActive()) continue;
            
            // 检查优先级条件
            if (waitingRequest.getPriority() > newPriority) {
                // 高优先级立即抢占
                evictFromService(roomId);
                assignToService(waitingRoomId);
                return;
            } else if (waitingRequest.getPriority() == newPriority) {
                // 相同优先级，检查等待时长是否达到2分钟
                int waitingTime = waitingTimeCounter.getOrDefault(waitingRoomId, 0);
                if (waitingTime >= 2) {
                    // 时间片调度：等待时长最长的房间抢占
                    evictFromService(roomId);
                    assignToService(waitingRoomId);
                    return;
                    }
                }
            // 如果优先级更低，不能抢占，继续检查下一个
        }
    }
    
    // 检查优先级提升触发的立即调度
    public void checkPriorityIncrease(Integer roomId, int oldPriority, int newPriority) {
        // 如果房间在等待队列中，检查是否可以抢占服务队列中的房间
        if (!waitingQueue.contains(roomId)) {
            return; // 房间不在等待队列中，无需处理
        }
        
        // 使用优先队列的优势：直接peek()获取最容易被抢占的房间
        ServiceQueueItem mostEvictable = serviceQueue.peek();
        if (mostEvictable != null && newPriority > mostEvictable.getPriority()) {
            // 执行抢占
            evictFromService(mostEvictable.getRoomId());
            assignToService(roomId);
        }
    }
    
    // 处理等待队列（为空闲空调分配请求）
    private void processWaitingQueue() {
        List<Integer> availableAcIds = acService.getAvailableAirConditioners();
        
        while (!waitingQueue.isEmpty() && !availableAcIds.isEmpty()) {
            // 按优先级、等待时长和房间号排序等待队列
            List<Integer> sortedWaiting = waitingQueue.stream()
                    .sorted((r1, r2) -> {
                        AirConditionerRequest req1 = acService.getRoomRequest(r1);
                        AirConditionerRequest req2 = acService.getRoomRequest(r2);
                        if (req1 == null || req2 == null) return 0;
                        
                        // 先按优先级降序
                        int priorityCompare = Integer.compare(req2.getPriority(), req1.getPriority());
                        if (priorityCompare != 0) return priorityCompare;
                        
                        // 优先级相同按等待时长降序（等待时间长的优先）
                        int waitingTimeCompare = Integer.compare(
                                waitingTimeCounter.getOrDefault(r2, 0),
                                waitingTimeCounter.getOrDefault(r1, 0)
                        );
                        if (waitingTimeCompare != 0) return waitingTimeCompare;
                        
                        // 等待时长也相同，按房间号升序
                        return Integer.compare(r1, r2);
                    })
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            
            Integer roomId = sortedWaiting.get(0);
            AirConditionerRequest request = acService.getRoomRequest(roomId);
            
            if (request != null && request.isActive() && request.getAssignedAcId() == null) {
                Integer acId = availableAcIds.remove(0);
                boolean assigned = acService.assignAirConditioner(roomId, acId);
                
                if (assigned) {
                    waitingQueue.remove(roomId);
                    waitingTimeCounter.remove(roomId);
                    waitingStartTime.remove(roomId);
                    
                    // 添加到服务队列
                    ServiceQueueItem item = new ServiceQueueItem(roomId, request.getPriority(), 0);
                    serviceQueue.offer(item);
                    serviceMap.put(roomId, item);
                    
                    serviceTimeCounter.put(roomId, 0);
                    serviceStartTime.put(roomId, LocalDateTime.now());
                }
            } else {
                break;
            }
        }
    }
    
    // 更新房间温度
    private void updateTemperatures() {
        for (ServiceQueueItem item : serviceQueue) {
            Integer roomId = item.getRoomId();
            AirConditionerRequest request = acService.getRoomRequest(roomId);
            if (request != null && request.isActive() && request.getAssignedAcId() != null) {
                AirConditioner ac = acService.getAirConditioner(request.getAssignedAcId());
                if (ac != null && ac.getOn() != null && ac.getOn()) {
                    Room room = roomService.getRoomById(roomId).orElse(null);
                    if (room != null) {
                        double currentTemp = room.getCurrentTemp();
                        double targetTemp = ac.getTargetTemp();
                        double tempChange = 1.0 / ac.getFanSpeed().getTempChangeTime();
                        
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
                        
                        // 检查是否达到目标温度
                        if (Math.abs(currentTemp - targetTemp) < 0.1) {
                            // 达到目标温度，自动关闭空调并触发调度（触发情况4：到达目标温度）
                            checkTargetTemperatureReached(roomId);
                        }
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
        return serviceQueue.stream()
                .map(ServiceQueueItem::getRoomId)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    // 获取房间的服务时长（分钟）
    public Integer getServiceTime(Integer roomId) {
        return serviceTimeCounter.getOrDefault(roomId, 0);
    }
    
    // 获取房间的等待时长（分钟）
    public Integer getWaitingTime(Integer roomId) {
        return waitingTimeCounter.getOrDefault(roomId, 0);
    }
    
    // 通用调度检查：当有空调释放时调用
    public void checkSchedulingOnAcRelease() {
        // 处理等待队列，为新释放的空调分配请求
        processWaitingQueue();
    }
    
    // 统一的调度检查方法：涵盖所有四种触发情况
    public void performFullSchedulingCheck() {
        // 触发情况2：检查时间片调度（等待时长≥120秒的情况）
        checkTimeSliceScheduling();
        
        // 处理等待队列（为空闲空调分配请求）
        processWaitingQueue();
    }
    
    // 重新同步等待队列：从数据库重新加载活跃请求到等待队列
    public void resyncWaitingQueue() {
        // 清空当前等待队列
        waitingQueue.clear();
        waitingTimeCounter.clear();
        waitingStartTime.clear();
        
        // 从数据库获取所有活跃且未分配空调的请求
        List<AirConditionerRequest> activeRequests = acService.getAllActiveRequests();
        for (AirConditionerRequest request : activeRequests) {
            if (request.getAssignedAcId() == null) {
                // 添加到等待队列
                waitingQueue.offer(request.getRoomId());
                waitingTimeCounter.put(request.getRoomId(), 0);
                waitingStartTime.put(request.getRoomId(), LocalDateTime.now());
            }
        }
        
        // 立即处理等待队列
        processWaitingQueue();
    }
    
    // 检查服务队列中某个房间是否达到目标温度
    public void checkTargetTemperatureReached(Integer roomId) {
        if (serviceMap.containsKey(roomId)) {
            // 房间达到目标温度，释放空调
            AirConditionerRequest request = acService.getRoomRequest(roomId);
            if (request != null && request.getAssignedAcId() != null) {
                // 自动关闭空调
                acService.cancelRequest(roomId);
                // 注意：cancelRequest中已经会调用schedulerService.removeRequest，
                // 而removeRequest中已经会调用processWaitingQueue()
            }
        }
    }
} 