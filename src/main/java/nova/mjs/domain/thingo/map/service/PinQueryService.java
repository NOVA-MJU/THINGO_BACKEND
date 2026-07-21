package nova.mjs.domain.thingo.map.service;

import nova.mjs.domain.thingo.map.entity.Pin;

/**
 * 타 도메인(리뷰 등)이 장소(Pin) 정보를 조회할 때 의존하는 인터페이스.
 *
 * 도메인 간 Repository 직접 호출을 피하기 위한 조회 창구.
 * (Pin 엔티티 자체는 @ManyToOne 연관으로 참조 가능하나, 로딩/검증은 이 인터페이스로 한다)
 */
public interface PinQueryService {

    /**
     * id로 핀을 조회한다. 카테고리·그룹까지 함께 로딩되어 있어
     * type / category.code / category.group.code 를 바로 사용할 수 있다.
     *
     * @throws nova.mjs.domain.thingo.map.exception.PinNotFoundException 없을 때
     */
    Pin getPinById(Long pinId);
}
