package com.example.hotel.service;

import com.example.hotel.entity.Room;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 温度管理服务
 * 专门负责房间温度控制和回温逻辑
 */
@Service
public class TemperatureManagementService {
    
    private final RoomService roomService;
    private final ExecutorService temperatureRecoveryExecutor;
    
    // 房间回温任务引用
    private final Map<Integer, Future<?>> roomRecoveryTasks = new ConcurrentHashMap<>();
    
    // 回温线程状态锁
    private final Map<Integer, Object> roomTemperatureLocks = new ConcurrentHashMap<>();
    
    public TemperatureManagementService(RoomService roomService) {
        this.roomService = roomService;
        
        // 初始化线程池
        this.temperatureRecoveryExecutor = new ThreadPoolExecutor(
            2, 5, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(10),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // 初始化房间锁
        initializeRoomLocks();
    }
    
    private void initializeRoomLocks() {
        roomService.getAllRooms().forEach(room -> 
            roomTemperatureLocks.put(room.getRoomId(), new Object()));
    }
    
    /**
     * 启动房间回温过程
     * 只要房间没有被分配空调服务并且温度不是室温就要启动回温
     */
    public void startRoomTemperatureRecovery(Integer roomId) {
        Room room = roomService.getRoomById(roomId).orElse(null);
        if (room == null) {
            return;
        }
        
        // 检查是否需要回温：房间没有分配空调且温度不是初始温度
        if (room.getAssignedAcId() != null || 
            Math.abs(room.getCurrentTemp() - room.getInitialTemp()) < 0.1) {
            return;
        }
        
        synchronized (roomTemperatureLocks.getOrDefault(roomId, new Object())) {
            // 取消已存在的回温任务
            cancelRoomTemperatureRecovery(roomId);
            
            // 启动新的回温任务
            Future<?> task = temperatureRecoveryExecutor.submit(() -> {
                performTemperatureRecovery(roomId);
            });
            
            roomRecoveryTasks.put(roomId, task);
        }
    }
    
    /**
     * 停止房间回温过程
     */
    public void cancelRoomTemperatureRecovery(Integer roomId) {
        synchronized (roomTemperatureLocks.getOrDefault(roomId, new Object())) {
            Future<?> task = roomRecoveryTasks.remove(roomId);
            if (task != null && !task.isDone()) {
                task.cancel(true);
            }
        }
    }
    
    /**
     * 执行温度回温过程
     */
    private void performTemperatureRecovery(Integer roomId) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Room room = roomService.getRoomById(roomId).orElse(null);
                if (room == null) {
                    break;
                }
                
                synchronized (roomTemperatureLocks.getOrDefault(roomId, new Object())) {
                    // 检查是否仍需要回温
                    if (room.getAssignedAcId() != null || 
                        Math.abs(room.getCurrentTemp() - room.getInitialTemp()) < 0.1) {
                        break; // 不需要回温了
                    }
                    
                    // 计算回温方向和新温度
                    double currentTemp = room.getCurrentTemp();
                    double targetTemp = room.getInitialTemp();
                    double tempChange = 0.5; // 每分钟变化0.5度
                    
                    double newTemp;
                    if (currentTemp > targetTemp) {
                        // 温度过高，需要降温
                        newTemp = Math.max(targetTemp, currentTemp - tempChange);
                    } else {
                        // 温度过低，需要升温
                        newTemp = Math.min(targetTemp, currentTemp + tempChange);
                    }
                    
                    // 更新房间温度
                    roomService.updateRoomTemperature(roomId, newTemp);
                    
                    // 检查是否已经到达目标温度
                    if (Math.abs(newTemp - targetTemp) < 0.1) {
                        break; // 回温完成
                    }
                }
                
                // 等待10秒（系统中10秒等于1分钟）
                Thread.sleep(10000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // 清理任务引用
            roomRecoveryTasks.remove(roomId);
        }
    }
    
    /**
     * 检查房间是否正在回温
     */
    public boolean isRoomRecovering(Integer roomId) {
        Future<?> task = roomRecoveryTasks.get(roomId);
        return task != null && !task.isDone();
    }
    
    /**
     * 获取所有正在回温的房间
     */
    public Map<Integer, Future<?>> getAllRecoveringRooms() {
        return new ConcurrentHashMap<>(roomRecoveryTasks);
    }
    
    /**
     * 关闭服务（清理资源）
     */
    public void shutdown() {
        // 取消所有回温任务
        roomRecoveryTasks.values().forEach(task -> {
            if (!task.isDone()) {
                task.cancel(true);
            }
        });
        roomRecoveryTasks.clear();
        
        // 关闭线程池
        temperatureRecoveryExecutor.shutdown();
        try {
            if (!temperatureRecoveryExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                temperatureRecoveryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            temperatureRecoveryExecutor.shutdownNow();
        }
    }
} 