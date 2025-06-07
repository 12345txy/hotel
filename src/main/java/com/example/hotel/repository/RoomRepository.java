package com.example.hotel.repository;

import com.example.hotel.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Integer> {
    
    /**
     * 根据房间ID查询房间
     */
    Optional<Room> findByRoomId(Integer roomId);
    
    /**
     * 查询所有可用房间（未被占用）
     */
    List<Room> findByOccupiedFalse();
    
    /**
     * 查询所有被占用的房间
     */
    List<Room> findByOccupiedTrue();
    
    /**
     * 根据分配的空调ID查询房间
     */
    List<Room> findByAssignedAcId(Integer acId);
    
    /**
     * 查询已分配空调的房间
     */
    @Query("SELECT r FROM Room r WHERE r.assignedAcId IS NOT NULL")
    List<Room> findRoomsWithAssignedAc();
    
    /**
     * 查询未分配空调的房间
     */
    @Query("SELECT r FROM Room r WHERE r.assignedAcId IS NULL")
    List<Room> findRoomsWithoutAssignedAc();
    
    /**
     * 统计已分配空调的房间数量
     */
    @Query("SELECT COUNT(r) FROM Room r WHERE r.assignedAcId IS NOT NULL")
    long countRoomsWithAssignedAc();
} 