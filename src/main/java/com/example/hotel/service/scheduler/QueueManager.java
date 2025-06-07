package com.example.hotel.service.scheduler;

import com.example.hotel.entity.AirConditionerRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * 队列管理器
 * 使用更适合的数据结构管理等待队列和服务队列
 */
@Component
public class QueueManager {
    
    /**
     * 等待队列项
     */
    public static class WaitingQueueItem {
        private final Integer roomId;
        private final int priority;
        private final LocalDateTime waitingStartTime;
        private volatile int waitingTime; // 分钟
        
        public WaitingQueueItem(Integer roomId, int priority, LocalDateTime waitingStartTime) {
            this.roomId = roomId;
            this.priority = priority;
            this.waitingStartTime = waitingStartTime;
            this.waitingTime = 0;
        }
        
        // getters and setters
        public Integer getRoomId() { return roomId; }
        public int getPriority() { return priority; }
        public LocalDateTime getWaitingStartTime() { return waitingStartTime; }
        public int getWaitingTime() { return waitingTime; }
        public void setWaitingTime(int waitingTime) { this.waitingTime = waitingTime; }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WaitingQueueItem that = (WaitingQueueItem) o;
            return Objects.equals(roomId, that.roomId);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(roomId);
        }
    }
    
    /**
     * 服务队列项
     */
    public static class ServiceQueueItem {
        private final Integer roomId;
        private volatile int priority;
        private final LocalDateTime serviceStartTime;
        private volatile int serviceTime; // 分钟
        
        public ServiceQueueItem(Integer roomId, int priority, LocalDateTime serviceStartTime) {
            this.roomId = roomId;
            this.priority = priority;
            this.serviceStartTime = serviceStartTime;
            this.serviceTime = 0;
        }
        
        // getters and setters
        public Integer getRoomId() { return roomId; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        public LocalDateTime getServiceStartTime() { return serviceStartTime; }
        public int getServiceTime() { return serviceTime; }
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
    
    // 等待队列：按优先级高→等待时间长→房间号小排序
    private final PriorityBlockingQueue<WaitingQueueItem> waitingQueue = 
        new PriorityBlockingQueue<>(16, 
            Comparator.comparing(WaitingQueueItem::getPriority, Comparator.reverseOrder())
                      .thenComparing(WaitingQueueItem::getWaitingTime, Comparator.reverseOrder())
                      .thenComparing(WaitingQueueItem::getRoomId));
    
    // 服务队列：按优先级低→服务时间长→房间号小排序（最容易被抢占的在前）
    private final PriorityBlockingQueue<ServiceQueueItem> serviceQueue = 
        new PriorityBlockingQueue<>(3,
            Comparator.comparing(ServiceQueueItem::getPriority)
                      .thenComparing(ServiceQueueItem::getServiceTime, Comparator.reverseOrder())
                      .thenComparing(ServiceQueueItem::getRoomId));
    
    // 快速查找映射
    private final Map<Integer, WaitingQueueItem> waitingMap = new ConcurrentHashMap<>();
    private final Map<Integer, ServiceQueueItem> serviceMap = new ConcurrentHashMap<>();
    
    /**
     * 添加到等待队列
     */
    public void addToWaitingQueue(Integer roomId, int priority) {
        removeFromWaitingQueue(roomId); // 避免重复
        
        WaitingQueueItem item = new WaitingQueueItem(roomId, priority, LocalDateTime.now());
        waitingQueue.offer(item);
        waitingMap.put(roomId, item);
    }
    
    /**
     * 从等待队列移除
     */
    public void removeFromWaitingQueue(Integer roomId) {
        WaitingQueueItem item = waitingMap.remove(roomId);
        if (item != null) {
            waitingQueue.remove(item);
        }
    }
    
    /**
     * 添加到服务队列
     */
    public void addToServiceQueue(Integer roomId, int priority) {
        ServiceQueueItem item = new ServiceQueueItem(roomId, priority, LocalDateTime.now());
        serviceQueue.offer(item);
        serviceMap.put(roomId, item);
    }
    
    /**
     * 从服务队列移除
     */
    public void removeFromServiceQueue(Integer roomId) {
        ServiceQueueItem item = serviceMap.remove(roomId);
        if (item != null) {
            serviceQueue.remove(item);
        }
    }
    
    /**
     * 获取最优先的等待房间（不移除）
     */
    public WaitingQueueItem peekWaitingQueue() {
        return waitingQueue.peek();
    }
    
    /**
     * 获取最容易被抢占的服务房间（不移除）
     */
    public ServiceQueueItem peekServiceQueue() {
        return serviceQueue.peek();
    }
    
    /**
     * 轮转：从等待队列移动到服务队列
     */
    public Integer rotateFromWaitingToService() {
        WaitingQueueItem waitingItem = waitingQueue.poll();
        if (waitingItem != null) {
            waitingMap.remove(waitingItem.getRoomId());
            addToServiceQueue(waitingItem.getRoomId(), waitingItem.getPriority());
            return waitingItem.getRoomId();
        }
        return null;
    }
    
    /**
     * 抢占：从服务队列移动到等待队列
     */
    public Integer evictFromServiceToWaiting() {
        ServiceQueueItem serviceItem = serviceQueue.poll();
        if (serviceItem != null) {
            serviceMap.remove(serviceItem.getRoomId());
            addToWaitingQueue(serviceItem.getRoomId(), serviceItem.getPriority());
            return serviceItem.getRoomId();
        }
        return null;
    }
    
    /**
     * 更新房间优先级
     */
    public void updateRoomPriority(Integer roomId, int newPriority) {
        // 更新服务队列中的优先级
        ServiceQueueItem serviceItem = serviceMap.get(roomId);
        if (serviceItem != null) {
            serviceItem.setPriority(newPriority);
            // 重新排序：移除再添加
            serviceQueue.remove(serviceItem);
            serviceQueue.offer(serviceItem);
        }
        
        // 等待队列中的优先级不变（等待队列的item是不可变的）
        // 如需更新，需要移除重新添加
    }
    
    /**
     * 更新时间计数器
     */
    public void updateTimeCounters() {
        // 更新等待时间
        waitingMap.values().forEach(item -> {
            item.setWaitingTime(item.getWaitingTime() + 1);
        });
        
        // 更新服务时间
        serviceMap.values().forEach(item -> {
            item.setServiceTime(item.getServiceTime() + 1);
        });
        
        // 重建队列以保证排序
        rebuildQueues();
    }
    
    /**
     * 重建队列（当时间更新时保证排序正确）
     */
    private void rebuildQueues() {
        // 重建等待队列
        List<WaitingQueueItem> waitingItems = new ArrayList<>(waitingQueue);
        waitingQueue.clear();
        waitingQueue.addAll(waitingItems);
        
        // 重建服务队列
        List<ServiceQueueItem> serviceItems = new ArrayList<>(serviceQueue);
        serviceQueue.clear();
        serviceQueue.addAll(serviceItems);
    }
    
    // 状态查询方法
    public int getWaitingQueueSize() { return waitingQueue.size(); }
    public int getServiceQueueSize() { return serviceQueue.size(); }
    public boolean isInWaitingQueue(Integer roomId) { return waitingMap.containsKey(roomId); }
    public boolean isInServiceQueue(Integer roomId) { return serviceMap.containsKey(roomId); }
    
    public List<Integer> getWaitingRoomIds() {
        return waitingMap.keySet().stream().sorted().toList();
    }
    
    public List<Integer> getServiceRoomIds() {
        return serviceMap.keySet().stream().sorted().toList();
    }
    
    public Integer getWaitingTime(Integer roomId) {
        WaitingQueueItem item = waitingMap.get(roomId);
        return item != null ? item.getWaitingTime() : null;
    }
    
    public Integer getServiceTime(Integer roomId) {
        ServiceQueueItem item = serviceMap.get(roomId);
        return item != null ? item.getServiceTime() : null;
    }
} 