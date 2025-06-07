package com.example.hotel.repository;

import com.example.hotel.entity.AirConditionerRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AirConditionerRequestRepository extends JpaRepository<AirConditionerRequest, Long> {
    
    /**
     * 根据房间ID查询最新的活跃请求
     */
    Optional<AirConditionerRequest> findByRoomIdAndActiveTrue(Integer roomId);
    
    /**
     * 查询所有活跃的请求
     */
    List<AirConditionerRequest> findByActiveTrueOrderByRequestTimeAsc();
    
    /**
     * 根据分配的空调ID查询活跃请求
     */
    List<AirConditionerRequest> findByAssignedAcIdAndActiveTrue(Integer acId);
    
    /**
     * 根据房间ID查询所有请求（包括历史）
     */
    List<AirConditionerRequest> findByRoomIdOrderByRequestTimeDesc(Integer roomId);
    
    /**
     * 根据时间范围查询请求
     */
    @Query("SELECT r FROM AirConditionerRequest r WHERE r.requestTime >= :startTime AND r.requestTime <= :endTime ORDER BY r.requestTime DESC")
    List<AirConditionerRequest> findByTimeRange(@Param("startTime") LocalDateTime startTime, 
                                               @Param("endTime") LocalDateTime endTime);
    
    /**
     * 统计活跃请求数量
     */
    long countByActiveTrue();
    
    /**
     * 查询等待分配空调的请求（活跃且未分配空调）
     */
    @Query("SELECT r FROM AirConditionerRequest r WHERE r.active = true AND r.assignedAcId IS NULL ORDER BY r.priority DESC, r.requestTime ASC")
    List<AirConditionerRequest> findWaitingRequests();
    
    /**
     * 查询已分配空调的活跃请求
     */
    @Query("SELECT r FROM AirConditionerRequest r WHERE r.active = true AND r.assignedAcId IS NOT NULL ORDER BY r.requestTime ASC")
    List<AirConditionerRequest> findActiveAssignedRequests();
} 