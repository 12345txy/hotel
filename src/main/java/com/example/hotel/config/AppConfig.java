package com.example.hotel.config;

import com.example.hotel.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AppConfig {

    @Autowired
    private RoomService roomService;

//    @Bean
    @PostConstruct
    public void initializeData() {
        // 初始化房间数据
        roomService.initializeRooms();
    }
}
