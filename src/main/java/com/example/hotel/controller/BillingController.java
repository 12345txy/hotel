package com.example.hotel.controller;

import com.example.hotel.entity.Bill;
import com.example.hotel.entity.BillDetail;
import com.example.hotel.service.BillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/billing")
public class BillingController {
    
    private final BillingService billingService;
    
    @Autowired
    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }
    
    @GetMapping("/{roomId}")
    public ResponseEntity<Bill> getBill(@PathVariable Integer roomId) {
        Bill bill = billingService.generateBill(roomId);
        if (bill != null) {
            return ResponseEntity.ok(bill);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/{roomId}/details")
    public ResponseEntity<Resource> downloadDetails(@PathVariable Integer roomId) {
        try {
            String fileName = billingService.exportDetailsToExcel(roomId);
            Path path = Paths.get("bills").resolve(fileName);
            Resource resource = new UrlResource(path.toUri());
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/{roomId}/bill")
    public ResponseEntity<Resource> downloadBill(@PathVariable Integer roomId) {
        try {
            Bill bill = billingService.generateBill(roomId);
            if (bill == null) {
                return ResponseEntity.notFound().build();
            }
            
            String fileName = billingService.exportBillToExcel(bill);
            Path path = Paths.get("bills").resolve(fileName);
            Resource resource = new UrlResource(path.toUri());
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
} 