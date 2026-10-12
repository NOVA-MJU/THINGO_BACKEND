package nova.mjs.domain.thingo.keywordAlarm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.keywordAlarm.dto.DeviceDTO;
import nova.mjs.domain.thingo.keywordAlarm.entity.Device;
import nova.mjs.domain.thingo.keywordAlarm.repository.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 비로그인 기기 식별자 발급/검증 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceService {

    private final DeviceRepository deviceRepository;

    /** 새 기기 id 를 발급한다(앱 최초 실행 시 1회). */
    @Transactional
    public DeviceDTO.Response.Issued issue() {
        Device device = deviceRepository.save(Device.issue());
        log.info("기기 id 발급 - deviceId={}", device.getId());
        return DeviceDTO.Response.Issued.of(device.getId());
    }

    /**
     * 서버가 발급한 기기 id 인지 확인한다. 형식이 틀리거나 발급 기록이 없으면 empty.
     * 확인된 기기는 마지막 사용 시각을 갱신한다(하루 1회까지만 실제 UPDATE).
     */
    @Transactional
    public Optional<UUID> findIssued(String rawDeviceId) {
        UUID deviceId = parse(rawDeviceId);
        if (deviceId == null) {
            return Optional.empty();
        }
        return deviceRepository.findById(deviceId)
                .map(device -> {
                    device.touch(Instant.now());
                    return device.getId();
                });
    }

    private UUID parse(String rawDeviceId) {
        if (rawDeviceId == null || rawDeviceId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawDeviceId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
