package com.example.hotel.controller;

import com.example.hotel.entity.BillDetail;
import com.example.hotel.service.BillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/bill-details")
@CrossOrigin(origins = "*")
public class BillDetailController {

    private final BillingService billingService;

    @Autowired
    public BillDetailController(BillingService billingService) {
        this.billingService = billingService;
    }

    /**
     * 获取指定账单的详单列表
     */
    @GetMapping("/bill/{billId}")
    public ResponseEntity<List<BillDetail>> getBillDetailsByBillId(@PathVariable Long billId) {
        List<BillDetail> details = billingService.getBillDetailsByBillId(billId);
        return ResponseEntity.ok(details);
    }

    /**
     * 获取指定房间的详单列表
     */
    @GetMapping("/room/{roomId}")
    public ResponseEntity<List<BillDetail>> getBillDetailsByRoomId(@PathVariable Integer roomId) {
        List<BillDetail> details = billingService.getBillDetailsByRoomId(roomId);
        return ResponseEntity.ok(details);
    }

    /**
     * 获取房间的活跃服务详单（未结束的服务）
     */
    @GetMapping("/room/{roomId}/active")
    public ResponseEntity<List<BillDetail>> getActiveServicesByRoomId(@PathVariable Integer roomId) {
        List<BillDetail> details = billingService.getActiveServicesByRoomId(roomId);
        return ResponseEntity.ok(details);
    }

    /**
     * 创建新的账单详单
     */
    @PostMapping
    public ResponseEntity<BillDetail> createBillDetail(@RequestBody CreateBillDetailRequest request) {
        BillDetail detail = billingService.createBillDetail(
                request.getBillId(),
                request.getRoomId(),
                request.getAcId(),
                request.getRequestTime(),
                request.getServiceStartTime(),
                request.getFanSpeed(),
                request.getMode(),
                request.getTargetTemp(),
                request.getRate()
        );
        return ResponseEntity.ok(detail);
    }

    /**
     * 更新详单的服务结束时间和费用
     */
    @PutMapping("/{detailId}/end-service")
    public ResponseEntity<BillDetail> endService(@PathVariable Long detailId,
                                                 @RequestBody EndServiceRequest request) {
        BillDetail detail = billingService.updateBillDetailServiceEnd(
                detailId,
                request.getEndTime(),
                request.getCost(),
                request.getEnergyConsumed()
        );
        
        if (detail != null) {
            return ResponseEntity.ok(detail);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 获取房间的总费用
     */
    @GetMapping("/room/{roomId}/total-cost")
    public ResponseEntity<Double> getTotalCostByRoomId(@PathVariable Integer roomId) {
        Double totalCost = billingService.getTotalCostByRoomId(roomId);
        return ResponseEntity.ok(totalCost);
    }

    /**
     * 获取房间在指定时间范围的能耗统计
     */
    @GetMapping("/room/{roomId}/energy-consumed")
    public ResponseEntity<Double> getEnergyConsumed(@PathVariable Integer roomId,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        Double energyConsumed = billingService.getEnergyConsumedByRoomAndTimeRange(roomId, startTime, endTime);
        return ResponseEntity.ok(energyConsumed);
    }

    /**
     * 保存账单详单
     */
    @PostMapping("/save")
    public ResponseEntity<BillDetail> saveBillDetail(@RequestBody BillDetail billDetail) {
        BillDetail savedDetail = billingService.saveBillDetail(billDetail);
        return ResponseEntity.ok(savedDetail);
    }

    /**
     * 批量保存账单详单
     */
    @PostMapping("/batch")
    public ResponseEntity<List<BillDetail>> saveBillDetails(@RequestBody List<BillDetail> billDetails) {
        List<BillDetail> savedDetails = billingService.saveBillDetails(billDetails);
        return ResponseEntity.ok(savedDetails);
    }

    // DTO类
    public static class CreateBillDetailRequest {
        private Long billId;
        private Integer roomId;
        private Integer acId;
        private LocalDateTime requestTime;
        private LocalDateTime serviceStartTime;
        private String fanSpeed;
        private String mode;
        private double targetTemp;
        private double rate;

        // Getters and Setters
        public Long getBillId() { return billId; }
        public void setBillId(Long billId) { this.billId = billId; }

        public Integer getRoomId() { return roomId; }
        public void setRoomId(Integer roomId) { this.roomId = roomId; }

        public Integer getAcId() { return acId; }
        public void setAcId(Integer acId) { this.acId = acId; }

        public LocalDateTime getRequestTime() { return requestTime; }
        public void setRequestTime(LocalDateTime requestTime) { this.requestTime = requestTime; }

        public LocalDateTime getServiceStartTime() { return serviceStartTime; }
        public void setServiceStartTime(LocalDateTime serviceStartTime) { this.serviceStartTime = serviceStartTime; }

        public String getFanSpeed() { return fanSpeed; }
        public void setFanSpeed(String fanSpeed) { this.fanSpeed = fanSpeed; }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public double getTargetTemp() { return targetTemp; }
        public void setTargetTemp(double targetTemp) { this.targetTemp = targetTemp; }

        public double getRate() { return rate; }
        public void setRate(double rate) { this.rate = rate; }
    }

    public static class EndServiceRequest {
        private LocalDateTime endTime;
        private double cost;
        private double energyConsumed;

        // Getters and Setters
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

        public double getCost() { return cost; }
        public void setCost(double cost) { this.cost = cost; }

        public double getEnergyConsumed() { return energyConsumed; }
        public void setEnergyConsumed(double energyConsumed) { this.energyConsumed = energyConsumed; }
    }
} 