package com.example.hotel.service;

import com.example.hotel.entity.Bill;
import com.example.hotel.entity.BillDetail;
import com.example.hotel.entity.Room;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class BillingService {

    private final RoomService roomService;
    private final AirConditionerService acService;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    public BillingService(RoomService roomService, AirConditionerService acService) {
        this.roomService = roomService;
        this.acService = acService;
    }
    // 生成账单
    public Bill generateBill(Integer roomId) {
        Room room = roomService.getRoomById(roomId).orElse(null);
        if (room == null || room.getCheckInTime() == null) {
            return null;
        }

        List<BillDetail> details = acService.getRoomBillDetails(roomId);

        // 计算空调总费用
        double acCost = details.stream().mapToDouble(BillDetail::getCost).sum();

        // 计算房费（使用 Bill 类中的 daysOfStay 字段）
        int days = room.getGuest().getStayDays()!= 0 ? room.getGuest().getStayDays(): 1; // 默认至少一天
        double roomCost = days * room.getPrice();

        // 创建账单
        return Bill.builder()
                .roomId(roomId)
                .checkInTime(room.getCheckInTime())
                .checkOutTime(room.getCheckOutTime())
                .daysOfStay(days)
                .roomCost(roomCost)
                .acCost(acCost)
                .totalCost(roomCost + acCost)
                .details(details)
                .build();
    }
    // 导出账单到Excel
    public String exportBillToExcel(Bill bill) throws IOException {
        // 创建Excel工作簿
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("账单");
        // 创建表头
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("房间号");
        headerRow.createCell(1).setCellValue("入住时间");
        headerRow.createCell(2).setCellValue("退房时间");
        headerRow.createCell(3).setCellValue("入住天数");
        headerRow.createCell(4).setCellValue("房费");
        headerRow.createCell(5).setCellValue("空调费用");
        headerRow.createCell(6).setCellValue("总费用");

        // 填充数据
        Row dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue(bill.getRoomId());
        dataRow.createCell(1).setCellValue(bill.getCheckInTime().format(formatter));
        dataRow.createCell(2).setCellValue(bill.getCheckOutTime() != null ?
                bill.getCheckOutTime().format(formatter) : "");
        dataRow.createCell(3).setCellValue(bill.getDaysOfStay());
        dataRow.createCell(4).setCellValue(bill.getRoomCost());
        dataRow.createCell(5).setCellValue(bill.getAcCost());
        dataRow.createCell(6).setCellValue(bill.getTotalCost());

        // 保存文件
        String fileName = "bill_room_" + bill.getRoomId() + ".xlsx";
        Path path = Paths.get("bills");
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        FileOutputStream fileOut = new FileOutputStream("bills/" + fileName);
        workbook.write(fileOut);
        fileOut.close();
        workbook.close();

        return fileName;
    }

    // 导出明细到Excel
    public String exportDetailsToExcel(Integer roomId) throws IOException {
        List<BillDetail> details = acService.getRoomBillDetails(roomId);

        // 创建Excel工作簿
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("明细");

        // 创建表头
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("序号");
        headerRow.createCell(1).setCellValue("房间号");
        headerRow.createCell(2).setCellValue("请求时间");
        headerRow.createCell(3).setCellValue("服务开始时间");
        headerRow.createCell(4).setCellValue("服务结束时间");
        headerRow.createCell(5).setCellValue("服务时长(分钟)");
        headerRow.createCell(6).setCellValue("风速");
        headerRow.createCell(7).setCellValue("费用(元)");
        headerRow.createCell(8).setCellValue("费率(元/度)");
        // 填充数据
        int rowNum = 1;
        for (BillDetail detail : details) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(rowNum - 1);
            row.createCell(1).setCellValue(detail.getRoomId());
            row.createCell(2).setCellValue(detail.getRequestTime().format(formatter));
            row.createCell(3).setCellValue(detail.getServiceStartTime().format(formatter));
            row.createCell(4).setCellValue(detail.getServiceEndTime().format(formatter));
            row.createCell(5).setCellValue(detail.getServiceDuration());
            row.createCell(6).setCellValue(detail.getFanSpeed().name());
            row.createCell(7).setCellValue(detail.getCost());
            row.createCell(8).setCellValue(detail.getRate());
        }
        // 保存文件
        String fileName = "details_room_" + roomId + ".xlsx";
        Path path = Paths.get("bills");
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        FileOutputStream fileOut = new FileOutputStream("bills/" + fileName);
        workbook.write(fileOut);
        fileOut.close();
        workbook.close();

        return fileName;
    }
}
