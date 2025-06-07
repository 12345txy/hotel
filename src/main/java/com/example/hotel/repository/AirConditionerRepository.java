package com.example.hotel.repository;

import com.example.hotel.entity.AirConditioner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AirConditionerRepository extends JpaRepository<AirConditioner, Integer> {
    
    /**
     * 根据空调ID查询空调
     */
    Optional<AirConditioner> findByAcId(Integer acId);
    
    /**
     * 查询所有可用空调（未服务任何房间）
     */
    @Query("SELECT a FROM AirConditioner a WHERE a.servingRoomId IS NULL")
    List<AirConditioner> findAvailableAirConditioners();
    
    /**
     * 查询正在服务的空调
     */
    @Query("SELECT a FROM AirConditioner a WHERE a.servingRoomId IS NOT NULL")
    List<AirConditioner> findActiveAirConditioners();
    
    /**
     * 根据服务房间ID查询空调
     */
    Optional<AirConditioner> findByServingRoomId(Integer roomId);
    
    /**
     * 统计可用空调数量
     */
    @Query("SELECT COUNT(a) FROM AirConditioner a WHERE a.servingRoomId IS NULL")
    long countAvailableAirConditioners();
} 