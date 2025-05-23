package com.example.hotel.config;

import com.example.hotel.service.RoomService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AppConfig {
    
    @Bean
    public void initializeData(RoomService roomService) {
        // 初始化房间数据
        roomService.initializeRooms();
    }
} 