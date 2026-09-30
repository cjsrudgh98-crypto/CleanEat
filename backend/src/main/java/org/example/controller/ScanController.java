package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.scan.BarcodeScanRequest;
import org.example.dto.scan.ReceiptScanRequest;
import org.example.dto.scan.ReceiptScanResponse;
import org.example.dto.scan.ScanResultResponse;
import org.example.service.ScanService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// 비로그인도 스캔 가능 - 로그인하면 내 알레르기 경고, 식단 우선 추천, 검사 기록 저장이 더해진다
@RestController
@RequestMapping("/api/scan")
@RequiredArgsConstructor
public class ScanController {

    private final ScanService scanService;

    @PostMapping("/barcode")
    public ResponseEntity<ScanResultResponse> scanBarcode(@Valid @RequestBody BarcodeScanRequest request,
                                                          Authentication authentication) {
        return ResponseEntity.ok(scanService.scanBarcode(request.getBarcode(), authentication));
    }

    @PostMapping("/image")
    public ResponseEntity<ScanResultResponse> scanImage(@RequestParam("file") MultipartFile file,
                                                        Authentication authentication) {
        return ResponseEntity.ok(scanService.scanImage(file, authentication));
    }

    @PostMapping("/receipt")
    public ResponseEntity<ReceiptScanResponse> scanReceipt(@Valid @RequestBody ReceiptScanRequest request,
                                                           Authentication authentication) {
        return ResponseEntity.ok(scanService.scanReceipt(request.getCode(), authentication));
    }

    // 영수증 사진의 상품명을 읽어서 상품을 찾는다 (QR/바코드가 없는 일반 영수증용)
    @PostMapping("/receipt-image")
    public ResponseEntity<ReceiptScanResponse> scanReceiptImage(@RequestParam("file") MultipartFile file,
                                                                Authentication authentication) {
        return ResponseEntity.ok(scanService.scanReceiptImage(file, authentication));
    }
}
