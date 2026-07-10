package nova.mjs.domain.thingo.report.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.report.dto.ReportDTO;
import nova.mjs.domain.thingo.report.service.ReportService;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 신고 접수.
     * - 로그인 필요
     * - 신고 사유 + (기타 시) 상세 내용을 받아 DB 기록 후 관리자에게 메일 발송
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReportDTO.Response.Detail>> createReport(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody ReportDTO.Request.Create request
    ) {
        ReportDTO.Response.Detail response =
                reportService.createReport(userPrincipal.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
