package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.domain.ScanHistory;
import org.example.dto.common.PageResponse;
import org.example.dto.history.ScanHistoryResponse;
import org.example.service.ReportService;
import org.example.service.ScanHistoryService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{id}/history")
@RequiredArgsConstructor
public class ScanHistoryController {

    private final ScanHistoryService scanHistoryService;
    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<PageResponse<ScanHistoryResponse>> getHistory(@PathVariable("id") Long userId,
                                                                        @RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "20") int size,
                                                                  Authentication authentication) {
        return ResponseEntity.ok(scanHistoryService.getHistory(userId, page, size, authentication));
    }

    @GetMapping("/{historyId}/report")
    public ResponseEntity<byte[]> downloadReport(@PathVariable("id") Long userId,
                                                   @PathVariable("historyId") Long historyId,
                                                   Authentication authentication) {
        ScanHistory history = scanHistoryService.getOne(userId, historyId, authentication);
        byte[] pdf = reportService.generateScanReport(history);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("cleaneat-report-" + historyId + ".pdf").build().toString())
                .body(pdf);
    }

    @DeleteMapping("/{historyId}")
    public ResponseEntity<Void> deleteOne(@PathVariable("id") Long userId,
                                           @PathVariable("historyId") Long historyId,
                                           Authentication authentication) {
        scanHistoryService.deleteOne(userId, historyId, authentication);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll(@PathVariable("id") Long userId, Authentication authentication) {
        scanHistoryService.deleteAll(userId, authentication);
        return ResponseEntity.noContent().build();
    }
}
