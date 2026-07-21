package nova.mjs.domain.thingo.map.service;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.map.exception.PinNotFoundException;
import nova.mjs.domain.thingo.map.repository.PinRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PinQueryService 구현. 카테고리/그룹을 fetch join으로 함께 로딩한다.
 */
@Service
@RequiredArgsConstructor
public class PinQueryServiceImpl implements PinQueryService {

    private final PinRepository pinRepository;

    @Override
    @Transactional(readOnly = true)
    public Pin getPinById(Long pinId) {
        // 장소 조회: 카테고리·그룹까지 fetch join. 없으면 MAP_PIN_NOT_FOUND
        return pinRepository.findByIdWithCategoryGroup(pinId)
                .orElseThrow(PinNotFoundException::new);
    }
}
