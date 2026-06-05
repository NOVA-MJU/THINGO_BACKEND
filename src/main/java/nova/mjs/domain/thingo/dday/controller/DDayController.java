package nova.mjs.domain.thingo.dday.controller;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.dday.dto.DDayDto;
import nova.mjs.domain.thingo.dday.service.DDayService;
import nova.mjs.util.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ddays")
@RequiredArgsConstructor
public class DDayController {

    private final DDayService ddayService;

    /**
     * 임박순 디데이 목록 조회.
     *
     * @param limit 노출 개수 (프론트 지정, 미지정 시 기본 4개)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<DDayDto.Response>>> getDDays(
            @RequestParam(value = "limit", defaultValue = "4") int limit
    ) {
        List<DDayDto.Response> ddays = ddayService.getDDays(limit);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(ddays));
    }
}
