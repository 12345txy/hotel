package com.example.hotel.repository;

import com.example.hotel.entity.BillDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BillDetailRepository extends JpaRepository<BillDetail, Long> {
    
    /**
     * 根据账单ID查询详单
     */
    List<BillDetail> findByBillId(Long billId);
    
    /**
     * 根据房间号查询详单
     */
    List<BillDetail> findByRoomId(Integer roomId);
    
    /**
     * 根据房间号和时间范围查询详单
     */
    @Query("SELECT bd FROM BillDetail bd WHERE bd.roomId = :roomId " +
           "AND bd.serviceStartTime >= :startTime AND bd.serviceEndTime <= :endTime " +
           "ORDER BY bd.serviceStartTime DESC")
    List<BillDetail> findByRoomIdAndTimeRange(@Param("roomId") Integer roomId,
                                             @Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);
    
    /**
     * 根据账单ID和时间范围查询详单
     */
    @Query("SELECT bd FROM BillDetail bd WHERE bd.billId = :billId " +
           "AND bd.serviceStartTime >= :startTime AND bd.serviceEndTime <= :endTime " +
           "ORDER BY bd.serviceStartTime ASC")
    List<BillDetail> findByBillIdAndTimeRange(@Param("billId") Long billId,
                                             @Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);
    
    /**
     * 查询指定时间范围内的所有详单
     */
    @Query("SELECT bd FROM BillDetail bd WHERE bd.serviceStartTime >= :startTime " +
           "AND bd.serviceEndTime <= :endTime ORDER BY bd.serviceStartTime DESC")
    List<BillDetail> findByTimeRange(@Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);
    
    /**
     * 统计房间的总费用
     */
    @Query("SELECT COALESCE(SUM(bd.cost), 0) FROM BillDetail bd WHERE bd.roomId = :roomId")
    Double sumCostByRoomId(@Param("roomId") Integer roomId);
    
    /**
     * 统计账单的详单费用
     */
    @Query("SELECT COALESCE(SUM(bd.cost), 0) FROM BillDetail bd WHERE bd.billId = :billId")
    Double sumCostByBillId(@Param("billId") Long billId);
    
    /**
     * 统计房间在指定时间范围的能耗
     */
    @Query("SELECT COALESCE(SUM(bd.energyConsumed), 0) FROM BillDetail bd " +
           "WHERE bd.roomId = :roomId AND bd.serviceStartTime >= :startTime " +
           "AND bd.serviceEndTime <= :endTime")
    Double sumEnergyConsumedByRoomAndTimeRange(@Param("roomId") Integer roomId,
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);
    
    /**
     * 查询未完成的服务详单（service_end_time为空）
     */
    @Query("SELECT bd FROM BillDetail bd WHERE bd.serviceEndTime IS NULL ORDER BY bd.serviceStartTime DESC")
    List<BillDetail> findActiveServices();
    
    /**
     * 查询房间的未完成服务详单
     */
    @Query("SELECT bd FROM BillDetail bd WHERE bd.roomId = :roomId AND bd.serviceEndTime IS NULL")
    List<BillDetail> findActiveServicesByRoomId(@Param("roomId") Integer roomId);
} 