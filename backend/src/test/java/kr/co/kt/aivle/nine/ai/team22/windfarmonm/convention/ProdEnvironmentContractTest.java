package kr.co.kt.aivle.nine.ai.team22.windfarmonm.convention;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 프로파일이 <b>기본값 없이</b> 요구하는 환경변수가 ECS 태스크 정의에 실제로 있는지 검사한다.
 * <p>
 * 이 테스트가 있는 이유: {@code application-prod.yaml} 에 {@code ${VAR}} 를 기본값 없이 선언하면
 * 미설정 시 컨텍스트 기동이 실패한다(의도된 설계다 — 운영 시크릿을 조용히 기본값으로 때우지 않기 위함).
 * 그런데 CI 는 test 프로파일로만 돌기 때문에 <b>운영에서만 터지는 이 실패를 초록불로 통과시킨다.</b>
 * 실제로 스케줄러 락 접두어를 필수로 만들면서 태스크 정의를 함께 고치지 않아 배포가 깨질 뻔했다.
 * <p>
 * 배포는 저장소의 {@code task-definition.json} 을 이미지 태그만 바꿔 그대로 올리므로, 그 파일이
 * 운영 환경변수의 유일한 출처다. 두 파일이 어긋나면 여기서 잡힌다.
 */
class ProdEnvironmentContractTest {

    /** {@code ${VAR}} — 콜론이 없으면 기본값이 없다는 뜻이다({@code ${VAR:default}} 는 대상 아님). */
    private static final Pattern REQUIRED_PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private static final Path PROD_YAML = Path.of("src/main/resources/application-prod.yaml");
    private static final Path TASK_DEFINITION = Path.of("task-definition.json");

    /**
     * 기본값이 있어({@code ${VAR:}}) 위 검사에는 걸리지 않지만, <b>운영에서는 값이 반드시 있어야</b> 하는 변수.
     * <p>
     * 이런 변수는 미설정이어도 기동에 성공하고 해당 기능만 조용히 폴백한다 — 그 조용함이 위험이다.
     * 실제로 KMA 두 변수는 한 번도 태스크 정의에 들어간 적이 없었고, 그동안 대시보드는 UNKNOWN 날씨를
     * FE 기본값(맑음 0도 0m/s)으로 그려 아무도 고장을 눈치채지 못했다.
     * <p>
     * 의도적으로 비워 두는 변수는 여기 넣지 말 것(예: {@code AWS_SAGEMAKER_DEFECT_ENDPOINT} 는
     * 엔드포인트 생성 전까지 빈 값이 정상이다 — 폴러만 휴면하고 요청은 큐에 남는다).
     */
    private static final Set<String> MANDATORY_IN_PROD_DESPITE_DEFAULT = Set.of(
            "KMA_BASE_URL",  // 미설정 시 대시보드 날씨가 전 발전소 UNKNOWN
            "KMA_API_KEY"    // 위와 동일 (평문 금지 — 이 저장소는 public 이라 Secrets Manager 로만)
    );

    @Test
    @DisplayName("운영 프로파일이 기본값 없이 요구하는 환경변수는 모두 태스크 정의에 있어야 한다")
    void everyRequiredProdVariableIsProvidedByTaskDefinition() throws IOException {
        Set<String> required = requiredVariables();
        Set<String> provided = providedVariables();

        assertThat(required)
                .as("""
                        %s 가 기본값 없이 요구하는 변수인데 %s 에 없다. \
                        이대로 배포하면 "Could not resolve placeholder" 로 컨테이너가 기동 실패한다. \
                        태스크 정의의 environment(평문) 또는 secrets(비밀값)에 추가할 것."""
                        .formatted(PROD_YAML, TASK_DEFINITION))
                .isSubsetOf(provided);
    }

    @Test
    @DisplayName("기본값이 있어도 운영에서 필수인 변수는 빈 값으로 두면 안 된다")
    void mandatoryProdVariablesAreNotLeftBlank() {
        assertThat(nonBlankVariables())
                .as("기능이 조용히 죽는 변수다. %s 의 environment 에 값이 있거나(평문)"
                        + " secrets 에 등록되어(비밀값) 있어야 한다.", TASK_DEFINITION)
                .containsAll(MANDATORY_IN_PROD_DESPITE_DEFAULT);
    }

    @Test
    @DisplayName("검사 대상이 비어 있지 않다(테스트가 무의미해지지 않도록)")
    void contractIsNotVacuous() {
        // 파일 경로가 바뀌거나 파싱이 깨져 빈 집합끼리 비교하면 위 테스트는 항상 통과한다.
        assertThat(requiredVariables()).as("운영 프로파일에서 필수 변수를 하나도 못 찾았다").isNotEmpty();
        assertThat(providedVariables()).as("태스크 정의에서 환경변수를 하나도 못 찾았다").isNotEmpty();
    }

    /** 운영 프로파일에서 기본값 없이 참조되는 환경변수 이름. */
    private static Set<String> requiredVariables() {
        String yaml = read(PROD_YAML);
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = REQUIRED_PLACEHOLDER.matcher(yaml);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /** 태스크 정의가 컨테이너에 주입하는 이름(평문 environment + Secrets Manager secrets). */
    private static Set<String> providedVariables() {
        JsonNode container = container();

        Set<String> names = new LinkedHashSet<>();
        container.path("environment").forEach(node -> names.add(node.path("name").asString()));
        container.path("secrets").forEach(node -> names.add(node.path("name").asString()));
        return names;
    }

    /** 실제로 값이 주입되는 이름만. environment 는 빈 문자열이면 제외하고, secrets 는 등록 자체가 값이다. */
    private static Set<String> nonBlankVariables() {
        JsonNode container = container();
        Set<String> names = new LinkedHashSet<>();
        container.path("environment").forEach(node -> {
            if (!node.path("value").asString("").isBlank()) {
                names.add(node.path("name").asString());
            }
        });
        container.path("secrets").forEach(node -> names.add(node.path("name").asString()));
        return names;
    }

    private static JsonNode container() {
        return JsonMapper.builder().build()
                .readTree(read(TASK_DEFINITION))
                .path("containerDefinitions")
                .path(0);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(path + " 를 읽을 수 없다(테스트 작업 디렉터리는 backend 모듈 루트다)", e);
        }
    }
}
