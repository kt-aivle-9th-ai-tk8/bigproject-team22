package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InferenceDispatchPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws.SageMakerInvoker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link InferenceDispatchPort} 어댑터. {@link SageMakerInvoker} 에 위임한다.
 */
@Component
@RequiredArgsConstructor
public class InferenceDispatchAdapter implements InferenceDispatchPort {

    private final SageMakerInvoker sageMakerInvoker;

    @Override
    public boolean isConfigured() {
        return sageMakerInvoker.isDefectEndpointConfigured();
    }

    @Override
    public void dispatch(String imageS3Uri, String inferenceId) {
        sageMakerInvoker.invokeDefectEndpointAsync(imageS3Uri, inferenceId);
    }
}
