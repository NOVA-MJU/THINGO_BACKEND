package nova.mjs.domain.thingo.keywordAlarm.controller;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.keywordAlarm.dto.DeviceDTO;
import nova.mjs.domain.thingo.keywordAlarm.service.DeviceService;
import nova.mjs.util.response.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비로그인 기기 식별자 발급 컨트롤러.
 *
 * - POST /api/v1/devices - 기기 id 발급 (로그인 불필요)
 *
 * 앱은 발급받은 deviceId 를 저장해 두고, 키워드 알림/알림함/FCM 토큰 요청에 X-Device-Id 헤더로 보낸다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping
    public ApiResponse<DeviceDTO.Response.Issued> issue() {
        return ApiResponse.success(deviceService.issue());
    }
}
