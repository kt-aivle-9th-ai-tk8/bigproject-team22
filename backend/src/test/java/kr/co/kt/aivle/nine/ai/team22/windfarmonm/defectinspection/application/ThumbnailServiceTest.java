package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionStoragePort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 썸네일 생성은 <b>표시 편의</b>이지 점검의 진행 조건이 아니다 — 실패가 위로 번지면 안 된다.
 */
@ExtendWith(MockitoExtension.class)
class ThumbnailServiceTest {

    @Mock
    InspectionStoragePort storagePort;
    @InjectMocks
    ThumbnailService service;

    @Test
    @DisplayName("점검 단위로 없는 썸네일만 만들도록 저장소에 위임한다")
    void delegatesToStorage() {
        given(storagePort.createMissingThumbnails(7L)).willReturn(3);

        service.generate(7L);

        verify(storagePort).createMissingThumbnails(7L);
    }

    @Test
    @DisplayName("저장소가 실패해도 예외를 밖으로 내보내지 않는다 — 조회는 원본으로 폴백된다")
    void swallowsStorageFailure() {
        given(storagePort.createMissingThumbnails(7L))
                .willThrow(new BusinessException(ErrorCode.STORAGE_NOT_CONFIGURED));

        assertThatCode(() -> service.generate(7L)).doesNotThrowAnyException();
    }
}
