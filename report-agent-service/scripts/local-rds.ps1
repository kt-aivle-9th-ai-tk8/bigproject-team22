<#
.SYNOPSIS
    로컬 RDS(MySQL) 검증 환경을 한 번에 준비한다.

.DESCRIPTION
    Docker 기동 → MySQL 컨테이너 → 마이그레이션(V1~) → CSV 시드 까지 전부 처리한다.
    이미 준비된 단계는 건너뛰므로 몇 번을 실행해도 안전하다(재부팅 후 복구도 같은 명령).

    실제 AWS RDS 가 아니라 '같은 스키마를 가진 로컬 MySQL' 이다. 개발·검증용이며,
    컨테이너를 지우면 데이터도 사라진다(볼륨을 쓰지 않는다 — 매번 깨끗한 상태가 낫다).

.PARAMETER Report
    준비가 끝난 뒤 이 id 로 보고서를 생성해 출력한다.
    -Type 이 defect(기본)면 report_id(5001~5060), anomaly 면 event_id(예: 2).

.PARAMETER Type
    생성할 보고서 종류. 기본 defect. 예: -Type anomaly (event_id 를 -Report 로 준다).

.PARAMETER Reseed
    이미 시드돼 있어도 --replace 로 다시 적재한다(컨테이너·마이그레이션은 그대로 두고 데이터만 갱신).
    CSV 를 고쳤을 때 -Reset(컨테이너 재생성)보다 빠르게 재적재하는 용도.
    이상감지 4테이블은 defect 체인과 함께 항상 시드된다(팀 공용 data/ 세트 기준, 별도 플래그 없음).

.PARAMETER Reset
    컨테이너를 지우고 처음부터 다시 만든다. 스키마나 시드를 고쳤을 때 쓴다.

.PARAMETER Down
    컨테이너를 지우고 끝낸다(정리용).

.PARAMETER NoAnalysis
    LLM 종합분석을 끄고 생성한다. 비용 0 이고 결과가 항상 같다.

.PARAMETER Check
    보고서 4종이 각각 어떤 테이블을 CSV/RDS 어디에서 읽는지 표로 보여준다.
    등재만 해두고 실제로는 CSV 로 떨어지는 상황을 눈으로 구별할 수 없어서 필요하다.

.PARAMETER Strict
    -Check 와 함께 쓴다. CSV 로 떨어지는 테이블이 하나라도 있으면 실패시킨다(종료코드 1).
    새 테이블을 RDS 로 옮긴 뒤 '정말 넘어갔는지' 확인하는 용도.

.EXAMPLE
    .\scripts\local-rds.ps1
    .\scripts\local-rds.ps1 -Report 5001
    .\scripts\local-rds.ps1 -Check
    .\scripts\local-rds.ps1 -Check -Strict
    .\scripts\local-rds.ps1 -Reset -Report 5009
    .\scripts\local-rds.ps1 -Reset -Check -Strict                    # anomaly 포함 RDS 검증
    .\scripts\local-rds.ps1 -Report 2 -Type anomaly                 # anomaly 보고서 생성
    .\scripts\local-rds.ps1 -Reseed                                 # CSV 고친 뒤 데이터만 재적재
    .\scripts\local-rds.ps1 -Down
#>
[CmdletBinding()]
param(
    [int]$Report = 0,
    [switch]$Reset,
    [switch]$Down,
    [switch]$NoAnalysis,
    [switch]$Check,
    [switch]$Strict,
    [switch]$Reseed,
    [string]$Type = 'defect'
)

$Ct   = 'mig-verify'
$Db   = 'windfarmonm'
$Pw   = 'verify'
$Port = 13306
$DbUrl = "mysql+pymysql://root:$Pw@127.0.0.1:$Port/$Db`?charset=utf8mb4"

$Here     = Split-Path -Parent $MyInvocation.MyCommand.Path
$SvcRoot  = Split-Path -Parent $Here
$RepoRoot = Split-Path -Parent $SvcRoot
$MigDir   = Join-Path $RepoRoot 'backend\src\main\resources\db\migration'

function Say($m) { Write-Host $m }
function Die($m) { Write-Host "[중단] $m" -ForegroundColor Red; exit 1 }

# mysql 클라이언트를 컨테이너 안에서 실행한다. -N -B 는 헤더/장식 없는 출력.
# 비밀번호는 MYSQL_PWD 로 넘긴다 — PowerShell 5.1 은 "-p$Pw" 형태의 인자를 mysql 이 못 알아보게
# 전달해서(리터럴 -pverify 는 되는데 변수로 만들면 Access denied) 인증이 실패한다.
# 겸사겸사 "Using a password on the command line is insecure" 경고도 없어진다.
function Sql($query) {
    docker exec -e MYSQL_PWD=$Pw $Ct mysql -uroot --default-character-set=utf8mb4 -N -B $Db -e $query 2> $null
}

# ── 정리만 하고 끝 ───────────────────────────────────────────────────────────
if ($Down) {
    docker rm -f $Ct > $null 2> $null
    Say "컨테이너 $Ct 제거됨."
    exit 0
}

# ── 1. Docker 데몬 ───────────────────────────────────────────────────────────
Say '=== 1) Docker'
docker info > $null 2> $null
if ($LASTEXITCODE -ne 0) {
    $exe = "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
    if (-not (Test-Path $exe)) { Die 'Docker Desktop 이 설치돼 있지 않다.' }
    Say '    Docker Desktop 기동 중 (1~2분 걸린다)...'
    Start-Process $exe
    $ok = $false
    foreach ($i in 1..90) {
        Start-Sleep -Seconds 2
        docker info > $null 2> $null
        if ($LASTEXITCODE -eq 0) { $ok = $true; break }
    }
    if (-not $ok) { Die 'Docker 데몬이 3분 안에 안 떴다. Docker Desktop 창에서 로그인/약관 동의가 필요할 수 있다.' }
}
Say '    데몬 동작중 ✓'

# ── 2. 컨테이너 ──────────────────────────────────────────────────────────────
Say '=== 2) MySQL 컨테이너'
if ($Reset) {
    docker rm -f $Ct > $null 2> $null
    Say '    -Reset : 기존 컨테이너 제거'
}

$state = docker ps -a --filter "name=^$Ct$" --format '{{.State}}' 2> $null
if (-not $state) {
    Say "    새로 생성 (localhost:$Port)"
    # 127.0.0.1 에만 바인딩한다. "-p 13306:3306" 은 0.0.0.0 이라 같은 네트워크의 다른 기기에서도
    # 붙을 수 있는데, 이 컨테이너는 비밀번호가 'verify' 인 검증용이라 노출되면 안 된다.
    # (root@% 계정은 공식 이미지가 MYSQL_ROOT_HOST 기본값 % 로 이미 만들어 주므로 호스트 접속은 된다.)
    docker run -d --name $Ct -p "127.0.0.1:${Port}:3306" `
        -e MYSQL_ROOT_PASSWORD=$Pw -e MYSQL_DATABASE=$Db `
        mysql:8 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci > $null
    if ($LASTEXITCODE -ne 0) { Die '컨테이너 생성 실패.' }
}
elseif ($state -ne 'running') {
    Say '    기존 컨테이너 기동'
    docker start $Ct > $null
    if ($LASTEXITCODE -ne 0) { Die '컨테이너 기동 실패.' }
}
else {
    Say '    이미 실행중 ✓'
}

# 준비 대기: mysqladmin ping 은 쓰면 안 된다 — 초기화 중의 임시 서버에도 응답해서
# root 비밀번호가 설정되기 전에 통과해버린다. 실제 인증이 필요한 쿼리로 확인한다.
Write-Host '    준비 대기' -NoNewline
$ready = $false
foreach ($i in 1..90) {
    if (Sql 'SELECT 1') { $ready = $true; break }
    Write-Host '.' -NoNewline
    Start-Sleep -Seconds 2
}
Write-Host ''
if (-not $ready) { Die "MySQL 이 3분 안에 준비되지 않았다.  진단: docker logs $Ct" }
Say '    준비 완료 ✓'

# ── 3. 마이그레이션 ──────────────────────────────────────────────────────────
Say '=== 3) 마이그레이션'
# 마이그레이션은 멱등하지 않다(CREATE TABLE 은 두 번 못 돈다). 이미 적용됐으면 건너뛴다.
$hasSchema = Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Db' AND table_name='defect'"
if ($hasSchema -eq '1') {
    Say '    이미 적용돼 있음 — 건너뜀 (다시 하려면 -Reset)'
}
else {
    if (-not (Test-Path $MigDir)) { Die "마이그레이션 폴더를 못 찾음: $MigDir" }
    # PowerShell 파이프로 SQL 을 흘려넣으면 한글 주석이 깨질 수 있다(콘솔 인코딩 경유).
    # 파일을 컨테이너에 복사해 컨테이너 안에서 읽게 하면 그 경로를 아예 안 탄다.
    docker cp "$MigDir" "${Ct}:/tmp/migration" > $null
    if ($LASTEXITCODE -ne 0) { Die '마이그레이션 파일 복사 실패.' }
    # sh 명령 안에 큰따옴표를 쓰면 안 된다 — PowerShell → 네이티브 exe 인자 전달 과정에서
    # 큰따옴표가 그룹 구분자로 소비돼 sh 가 "unexpected end of file" 로 죽는다.
    # cd 로 먼저 옮겨 파일명만 다루면 따옴표도 basename 도 필요 없다.
    $sh = "set -e; cd /tmp/migration; for f in V*.sql; do echo '    적용' `$f; " +
          "mysql -uroot --default-character-set=utf8mb4 $Db < `$f; done"
    docker exec -e MYSQL_PWD=$Pw $Ct sh -c $sh
    if ($LASTEXITCODE -ne 0) { Die '마이그레이션 적용 실패 (위 에러 확인).' }
    Say '    완료 ✓'
}

# ── 4. 시드 ──────────────────────────────────────────────────────────────────
Say '=== 4) CSV 시드'
# seed_rds.py 가 defect 체인 + 이상감지 4테이블(scada_record·anomaly_event·aws_record·asos_record)을
# 항상 함께 적재한다(팀 공용 data/ 세트 기준, 플래그 불필요).
Push-Location $SvcRoot
$rows = Sql 'SELECT COUNT(*) FROM report'
$seeded = ($rows -and [int]$rows -gt 0)
if ($seeded -and -not $Reseed) {
    Say "    이미 데이터 있음 (report $rows 행) — 건너뜀 (다시 넣으려면 -Reseed 또는 -Reset)"
    $seedFailed = $false
}
else {
    $seedArgs = @('scripts/seed_rds.py', '--db-url', $DbUrl)
    if ($seeded) { Say '    -Reseed : 데이터 재적재(--replace)'; $seedArgs += '--replace' }
    python @seedArgs
    $seedFailed = ($LASTEXITCODE -ne 0)
}
Pop-Location
if ($seedFailed) { Die '시드 실패 (위 에러 확인).' }

# ── 5. 안내 / 보고서 생성 ────────────────────────────────────────────────────
Say ''
Say '=== 준비 완료.  이 창에서 바로 쓰려면:'
Say "    `$env:DATA_SOURCE='rds'"
Say "    `$env:DB_URL='$DbUrl'"
Say "    `$env:PYTHONIOENCODING='utf-8'"

if ($Check -or $Report -gt 0) {
    $env:DATA_SOURCE = 'rds'
    $env:DB_URL = $DbUrl
    $env:PYTHONIOENCODING = 'utf-8'
}

$exit = 0

if ($Check) {
    Say ''
    Say '=== 데이터 출처 점검'
    Say ''
    if ($Strict) { $env:STRICT_RDS = 'true' } else { $env:STRICT_RDS = '' }
    Push-Location $SvcRoot
    python scripts/check_sources.py
    if ($LASTEXITCODE -ne 0) { $exit = $LASTEXITCODE }
    Pop-Location
    $env:STRICT_RDS = ''
}

if ($Report -gt 0) {
    Say ''
    Say "=== 보고서 생성 (type=$Type, id=$Report)"
    Say ''
    if ($NoAnalysis) { $env:REPORT_WITH_ANALYSIS = 'false' }

    Push-Location $SvcRoot
    python -c "from app.service import generate_report; r=generate_report('$Type', $Report); print(('# ' + r['title'] + '\n\n' + (r['context'] or '')) if r.get('found') else ('생성 실패: ' + str(r.get('error') or '대상 없음')))"
    if ($LASTEXITCODE -ne 0) { $exit = $LASTEXITCODE }
    Pop-Location
}

exit $exit
