package nova.mjs.domain.thingo.keywordAlarm.repository;

import nova.mjs.domain.thingo.keywordAlarm.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
}
