package nova.mjs.domain.thingo.keywordAlarm.service;

import nova.mjs.domain.thingo.keywordAlarm.entity.Device;
import nova.mjs.domain.thingo.keywordAlarm.repository.DeviceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @InjectMocks
    private DeviceService deviceService;

    @Test
    @DisplayName("발급 시 서버가 만든 UUID 를 돌려준다")
    void should_issue_server_generated_id() {
        given(deviceRepository.save(any(Device.class))).willAnswer(invocation -> invocation.getArgument(0));

        UUID first = deviceService.issue().getDeviceId();
        UUID second = deviceService.issue().getDeviceId();

        assertThat(first).isNotNull().isNotEqualTo(second);
    }

    @Test
    @DisplayName("UUID 형식이 아니면 DB 조회 없이 무효")
    void should_reject_malformed_id() {
        assertThat(deviceService.findIssued("not-a-uuid")).isEmpty();
        assertThat(deviceService.findIssued(" ")).isEmpty();
        assertThat(deviceService.findIssued(null)).isEmpty();
        verifyNoInteractions(deviceRepository);
    }

    @Test
    @DisplayName("발급 기록이 없는 UUID 는 무효")
    void should_reject_unknown_id() {
        UUID unknown = UUID.randomUUID();
        given(deviceRepository.findById(unknown)).willReturn(Optional.empty());

        assertThat(deviceService.findIssued(unknown.toString())).isEmpty();
    }

    @Test
    @DisplayName("발급된 기기는 유효하고, 하루 넘게 지난 마지막 사용 시각만 갱신한다")
    void should_accept_issued_id_and_touch_stale_last_seen() {
        Device device = Device.issue();
        Instant stale = Instant.now().minus(Duration.ofDays(2));
        ReflectionTestUtils.setField(device, "lastSeenAt", stale);
        given(deviceRepository.findById(device.getId())).willReturn(Optional.of(device));

        assertThat(deviceService.findIssued(device.getId().toString())).contains(device.getId());
        assertThat(device.getLastSeenAt()).isAfter(stale);

        Instant touched = device.getLastSeenAt();
        deviceService.findIssued(device.getId().toString());
        assertThat(device.getLastSeenAt()).isEqualTo(touched);
    }
}
