package com.example.hotel.controller;


import com.example.hotel.service.CheckOutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class CheckOutController {

    private final CheckOutService checkOutService;
    public CheckOutController(CheckOutService checkOutService) {
        this.checkOutService = checkOutService;
    }
    @PostMapping("/{roomId}")
    public ResponseEntity<String> checkOut(@PathVariable Integer roomId) {
        boolean success = checkOutService.checkOut(roomId);
        if (success) {
            return ResponseEntity.ok("退房成功");
        } else {
            return ResponseEntity.badRequest().body("退房失败");
        }
    }
}
