package com.example.hotel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HotelApplication {

	// 主方法
	public static void main(String[] args) {
		// 运行SpringApplication，并传入HotelApplication类和args参数
		SpringApplication.run(HotelApplication.class, args);
	}

}
