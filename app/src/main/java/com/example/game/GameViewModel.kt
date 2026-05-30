package com.example.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ScoreEntity
import com.example.data.ScoreRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// 3D 공간 상의 3D 좌표 데이터 모델
data class Point3D(val x: Float, val y: Float, val z: Float)

// 3D 유성 개체
data class Meteor3D(
    val id: Long,
    var x: Float, // 3D 우주 가상 X (-1000 ~ 1000)
    var y: Float, // 3D 우주 가상 Y (-1000 ~ 1000)
    var z: Float, // 3D 우주 가상 깊이 Z (100 ~ 2500)
    val radius: Float, // 3D 기본 반경
    val rotationSpeed: Float, // 회전율
    var rotationAngle: Float = 0f,
    val colorType: Int = Random.nextInt(3), // 다른 빛깔
    val speedFactor: Float = Random.nextFloat() * 0.4f + 0.8f // 유성별 속도 차이
)

// 3D 배경성 개체 (속도감을 시각화하는 아주 작은 성간 물질)
data class Star3D(
    var x: Float,
    var y: Float,
    var z: Float,
    val speed: Float
)

// 3D 빨간색 적기 개체 (30초마다 반대쪽에서 급습)
data class EnemyShip3D(
    val id: Long,
    var x: Float, // 3D 우주 가상 X
    var y: Float, // 3D 우주 가상 Y
    var z: Float, // 3D 우주 가상 깊이 Z
    val speed: Float, // 기동 물리 추진 속도
    val radius: Float = 88f, // 충돌 반경 대폭 상향으로 압도적 긴장감 배가 (기존 55f)
    var rotationAngle: Float = 0f,
    val isFromBehind: Boolean, // true -> 뒤에서 앞으로 기동, false -> 앞에서 뒤로 기동
    var isDestroyed: Boolean = false
)

// 폭발 3D 파티클 개체
data class Particle3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val vx: Float,
    val vy: Float,
    val vz: Float,
    val color: Int,
    var life: Float = 1.0f // 1.0 -> 0.0 소멸
)

enum class ItemType {
    SHIELD, // 쉴드를 즉시 1 회복 (최대 3개 유지)
    SLOW,   // 성간 크로노 동체 감속구 (5초간 주변 소행성/적선 속도 50% 감속)
    BOMB,   // 플라즈마 파워 셀 (플라즈마 폭탄 수량 즉시 +1 충전)
    CLEAN   // 보이드 클린 스위퍼 (3초간 가상 진공 보이드 클린 생성)
}

data class Item3D(
    val id: Long,
    val type: ItemType,
    var x: Float,
    var y: Float,
    var z: Float,
    var rotationAngle: Float = 0f,
    val speedFactor: Float = Random.nextFloat() * 0.3f + 0.8f,
    val radius: Float = 40f
)

enum class GameState {
    READY, PLAYING, GAMEOVER
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val scoreRepository: ScoreRepository
    val highScoreState: StateFlow<ScoreEntity?>
    val allScoresState: StateFlow<List<ScoreEntity>>

    init {
        val database = AppDatabase.getDatabase(application)
        scoreRepository = ScoreRepository(database.scoreDao())
        highScoreState = scoreRepository.highScore.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
        allScoresState = scoreRepository.allScores.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList<ScoreEntity>()
        )
    }

    // --- 게임 상태 핵심 변수들 ---
    private val _gameState = MutableStateFlow(GameState.READY)
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _score = MutableStateFlow(0f) // 현재 버틴 시간(초)
    val score: StateFlow<Float> = _score.asStateFlow()

    private val _shield = MutableStateFlow(3) // 남은 방어막 수 (최대 3)
    val shield: StateFlow<Int> = _shield.asStateFlow()

    private val _bombCount = MutableStateFlow(0) // 사용 가능한 폭탄 수 (처음엔 하나도 없음)
    val bombCount: StateFlow<Int> = _bombCount.asStateFlow()

    private val _bombGauge = MutableStateFlow(0f) // 15초 폭탄 충전 게이지 (0f ~ 1f)
    val bombGauge: StateFlow<Float> = _bombGauge.asStateFlow()

    // 쉴드 타격 충돌 시 화면 떨림 피드백 애니메이션 프레임
    private val _shieldDamageTime = MutableStateFlow(0L)
    val shieldDamageTime: StateFlow<Long> = _shieldDamageTime.asStateFlow()

    // 부스트 가속 기동 유무 (더블 터치 대응)
    private val _isBoosting = MutableStateFlow(false)
    val isBoosting: StateFlow<Boolean> = _isBoosting.asStateFlow()

    fun setBoosting(boosting: Boolean) {
        _isBoosting.value = boosting
    }

    // --- 3D 전술 아이템 목록 및 관리 ---
    val items = MutableStateFlow<List<Item3D>>(emptyList())
    private var lastItemSpawnScore = 0f

    // 크로노 타임 슬로우 남은 시간 (초)
    private val _chronoSlowTime = MutableStateFlow(0f)
    val chronoSlowTime: StateFlow<Float> = _chronoSlowTime.asStateFlow()

    // 3초간 클린지역 활성화 남은 시간 (초)
    private val _cleanTime = MutableStateFlow(0f)
    val cleanTime: StateFlow<Float> = _cleanTime.asStateFlow()

    // 취득한 전술 아이템 안내 메시지 (한글 알림용)
    private val _itemMessage = MutableStateFlow<String?>(null)
    val itemMessage: StateFlow<String?> = _itemMessage.asStateFlow()
    private var itemMessageClearJob: Job? = null

    // 드래그로 조종하는 3D 우주선 중심 위치 (가상 공간 -350 ~ 350)
    private val _shipX = MutableStateFlow(0f)
    val shipX: StateFlow<Float> = _shipX.asStateFlow()

    private val _shipY = MutableStateFlow(0f)
    val shipY: StateFlow<Float> = _shipY.asStateFlow()

    // 우주선 3D 모션 각도 (기울기 피드백)
    private val _shipRoll = MutableStateFlow(0f) // 좌우 이동시 Roll 회전 각도
    val shipRoll: StateFlow<Float> = _shipRoll.asStateFlow()

    private val _shipPitch = MutableStateFlow(0f) // 상하 이동시 Pitch 회전 각도
    val shipPitch: StateFlow<Float> = _shipPitch.asStateFlow()

    // 화면 가장자리 인접 상태 (충돌 위험 알림용)
    private val _isNearBorder = MutableStateFlow(false)
    val isNearBorder: StateFlow<Boolean> = _isNearBorder.asStateFlow()

    // 가장자리 경고 및 충돌 제어 시간들
    private var borderStayTime = 0f
    private var borderDamageCooldown = 0f

    // 게임 루프 및 아이템 트리거를 위한 Job
    private var gameLoopJob: Job? = null
    private var lastUpdateTime = 0L

    // 성간 배경 물질 3D 목록
    val stars = MutableStateFlow<List<Star3D>>(emptyList())

    // 3D 돌진 유성 목록
    val meteors = MutableStateFlow<List<Meteor3D>>(emptyList())

    // 3D 폭발 파티클 목록
    val particles = MutableStateFlow<List<Particle3D>>(emptyList())

    // 30초 주기 출현하는 빨간색 적기 목록
    val enemyShips = MutableStateFlow<List<EnemyShip3D>>(emptyList())
    private var lastEnemySpawnScore = 0f
    private var nextEnemySpawnInterval = 20f // 15~30초 랜덤 값

    // 캔버스 실제 화면 해상도 동적 트래킹용
    private val _screenWidth = MutableStateFlow(1080f)
    private val _screenHeight = MutableStateFlow(1920f)

    fun updateScreenBounds(width: Float, height: Float) {
        if (width > 0f) _screenWidth.value = width
        if (height > 0f) _screenHeight.value = height
    }

    // 폭탄 발사 쇼크웨이브 연출
    private val _shockwaveRadius = MutableStateFlow(0f) // 0f ~ 2000f
    val shockwaveRadius: StateFlow<Float> = _shockwaveRadius.asStateFlow()

    // 최초 3D 요소들 초기 세팅
    private fun spawnInitialStars() {
        val list = mutableListOf<Star3D>()
        for (i in 1..80) {
            list.add(
                Star3D(
                    x = (Random.nextFloat() * 3000f - 1500f),
                    y = (Random.nextFloat() * 3000f - 1500f),
                    z = (Random.nextFloat() * 2400f + 100f),
                    speed = (Random.nextFloat() * 15f + 10f)
                )
            )
        }
        stars.value = list
    }

    private fun spawnInitialMeteors() {
        val list = mutableListOf<Meteor3D>()
        for (i in 1..8) {
            list.add(createRandomMeteor(z = (1000f + i * 200f)))
        }
        meteors.value = list
    }

    private fun createRandomMeteor(z: Float = 2500f): Meteor3D {
        val paddingX = 160f
        val paddingY = 160f
        val maxX = ((_screenWidth.value / 2f) - paddingX) / 3f
        val maxY = ((_screenHeight.value / 2f) - paddingY) / 3f
        val clampX = maxX.coerceAtLeast(100f)
        val clampY = maxY.coerceAtLeast(100f)

        var spawnX = 0f
        var spawnY = 0f

        // 최대 5회 최적의 분산 위치 탐색 시도 (장애물끼리 서로 겹쳐서 몰려오는 현상 차단)
        for (attempt in 1..5) {
            // 조준 위협 비율을 기존 45%에서 15%로 대폭 축소하여 초반/후반 억까 몰매 현상 완전 해소
            val isTargeted = Random.nextFloat() < 0.15f
            val tempX = if (isTargeted) {
                // 플레이어 가동 구역 주변으로 넓고 유기적으로 퍼트리며 조준 (6.5배 확장)
                (Random.nextFloat() * (clampX * 6.5f) - (clampX * 3.25f))
            } else {
                // 심우주 3D 파노라마 안심 무주공산 영역으로 고루 스파스 스폰 (10.0배 확장)
                (Random.nextFloat() * (clampX * 10.0f) - (clampX * 5.0f))
            }

            val tempY = if (isTargeted) {
                (Random.nextFloat() * (clampY * 6.5f) - (clampY * 3.25f))
            } else {
                (Random.nextFloat() * (clampY * 10.0f) - (clampY * 5.0f))
            }

            spawnX = tempX
            spawnY = tempY

            // 스폰될 유성이 성간 내 다른 활성 유성과 너무 완벽히 포개지지 않도록 XY 거리 체크 적용
            val currentMeteors = meteors.value
            val isTooCloseToOthers = currentMeteors.any { 
                val dx = it.x - spawnX
                val dy = it.y - spawnY
                // 같은 전면 Z 깊이 권역(300f 미만 차이)에서 XY 간격이 복합 280dp 미만이면 과화 밀도로 판단해 재조정
                kotlin.math.abs(it.z - z) < 300f && (dx * dx + dy * dy) < 78400f
            }
            if (!isTooCloseToOthers) {
                break // 충분히 안전한 공간이 확보되었으므로 스폰 지점 확정
            }
        }

        return Meteor3D(
            id = Random.nextLong(),
            x = spawnX,
            y = spawnY,
            z = z,
            radius = (Random.nextFloat() * 25f + 25f), // 3D 크기 반경
            rotationSpeed = (Random.nextFloat() * 3f - 1.5f)
        )
    }

    // 드래그 조작 제스처 (상대적 오프셋을 사용해 부드럽게 한도 내 구동)
    fun moveShip(deltaX: Float, deltaY: Float) {
        if (_gameState.value != GameState.PLAYING) return

        // 화면 밖으로 기체가 완전히 이탈하는 슬라이드 링 현상을 방지하기 위해 가상 3D 좌표 클램프 연동
        // 3D 투영 z = 200f이고 focal = 600f이므로 원근 배율 3배 적용됨 (최소 마진 패딩 영역 160f 지정)
        val paddingX = 160f
        val paddingY = 160f
        val maxX = ((_screenWidth.value / 2f) - paddingX) / 3f
        val maxY = ((_screenHeight.value / 2f) - paddingY) / 3f

        val clampX = maxX.coerceAtLeast(100f)
        val clampY = maxY.coerceAtLeast(100f)

        _shipX.update { (it + deltaX * 1.5f).coerceIn(-clampX, clampX) }
        _shipY.update { (it + deltaY * 1.5f).coerceIn(-clampY, clampY) }

        // 이동 방향과 가속도에 비례해 3D 피치/롤 회전각을 세팅 (자연스러운 3D 뱅크 드래프트 기동)
        _shipRoll.update { (deltaX * -2.5f).coerceIn(-45f, 45f) }
        _shipPitch.update { (deltaY * 1.5f).coerceIn(-25f, 25f) }
    }

    // 제스처 해제 시 우주선 회전 각도를 서서히 수평 복원
    fun stabilizeShip() {
        if (_gameState.value != GameState.PLAYING) return
        _shipRoll.update { it * 0.85f }
        _shipPitch.update { it * 0.85f }
    }

    // 게임 시작
    fun startGame() {
        _gameState.value = GameState.PLAYING
        _score.value = 0f
        _shield.value = 3
        _bombCount.value = 0
        _bombGauge.value = 0f
        _shockwaveRadius.value = 0f
        _shipX.value = 0f
        _shipY.value = 0f
        _shipRoll.value = 0f
        _shipPitch.value = 0f
        _isBoosting.value = false
        _isNearBorder.value = false
        borderStayTime = 0f
        borderDamageCooldown = 0f
        particles.value = emptyList()
        enemyShips.value = emptyList()
        lastEnemySpawnScore = 0f
        nextEnemySpawnInterval = Random.nextFloat() * 15f + 15f // 15초~30초 랜덤 값
        items.value = emptyList()
        lastItemSpawnScore = 0f
        _chronoSlowTime.value = 0f
        _cleanTime.value = 0f
        _itemMessage.value = null
        itemMessageClearJob?.cancel()

        spawnInitialStars()
        spawnInitialMeteors()

        lastUpdateTime = System.currentTimeMillis()

        // 메인 게임 코루틴 작동 (약 60FPS)
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            while (_gameState.value == GameState.PLAYING) {
                val now = System.currentTimeMillis()
                val delta = (now - lastUpdateTime).coerceAtLeast(1L)
                lastUpdateTime = now

                updatePhysics(delta / 1000f)

                delay(16) // ~60FPS
            }
        }
    }

    // 3D 물리 엔진 및 물체 포지셔닝 업데이트 루프
    private fun updatePhysics(dtRaw: Float) {
        val boosting = _isBoosting.value
        // 더블터치 시 1.8배 속도 가속 부스터 적용
        val dt = if (boosting) dtRaw * 1.8f else dtRaw

        // 크로노 타임 슬로우 디크리먼트
        if (_chronoSlowTime.value > 0f) {
            _chronoSlowTime.update { (it - dtRaw).coerceAtLeast(0f) }
        }
        val slowActive = _chronoSlowTime.value > 0f

        // 3초간 클린지역 활성화 디크리먼트
        if (_cleanTime.value > 0f) {
            _cleanTime.update { (it - dtRaw).coerceAtLeast(0f) }
        }
        val cleanActive = _cleanTime.value > 0f

        val dtWorld = dt * (if (slowActive) 0.5f else 1.0f)

        // 0. 가장자리 충돌 및 체류 대미지 검사 (화면 모서리에 매달려 피해 다니는 불공정 행위 차단)
        val paddingX = 160f
        val paddingY = 160f
        val maxX = ((_screenWidth.value / 2f) - paddingX) / 3f
        val maxY = ((_screenHeight.value / 2f) - paddingY) / 3f
        val clampX = maxX.coerceAtLeast(100f)
        val clampY = maxY.coerceAtLeast(100f)

        val currentX = _shipX.value
        val currentY = _shipY.value

        // 기체가 95% 한계 영역 너머로 이탈 및 인접할 경우 경고 시작
        val isNear = kotlin.math.abs(currentX) >= clampX * 0.95f || kotlin.math.abs(currentY) >= clampY * 0.95f
        _isNearBorder.value = isNear

        if (isNear) {
            borderStayTime += dtRaw
            // 가장자리에 거의 완전히 닿았을경우 즉시 충돌 판정, 그 외에는 1초 이상 머물렀을 때 대미지
            val isHardHit = (kotlin.math.abs(currentX) >= clampX * 0.99f || kotlin.math.abs(currentY) >= clampY * 0.99f)
            if (borderDamageCooldown <= 0f && (borderStayTime >= 1.0f || isHardHit)) {
                _shieldDamageTime.value = System.currentTimeMillis()
                _shield.update { currentShield ->
                    val nextShield = currentShield - 1
                    if (nextShield <= 0) {
                        endGame()
                        0
                    } else {
                        nextShield
                    }
                }

                // 우주선 위치에서 경고 타격감 붉은색 파편 분출 효과 연출
                val list = mutableListOf<Particle3D>()
                for (i in 0 until 15) {
                    list.add(
                        Particle3D(
                            x = currentX,
                            y = currentY,
                            z = 30f,
                            vx = (Random.nextFloat() * 600f - 300f),
                            vy = (Random.nextFloat() * 600f - 300f),
                            vz = (Random.nextFloat() * 200f - 100f),
                            color = 0xFFFF3333.toInt()
                        )
                    )
                }
                particles.update { it + list }

                // 대미지 후 즉각적인 연사 상태를 피하기 위한 1.2초 쿨다운 적용
                borderDamageCooldown = 1.2f
                borderStayTime = 0f
            }
        } else {
            borderStayTime = 0f
        }

        if (borderDamageCooldown > 0f) {
            borderDamageCooldown -= dtRaw
        }

        // 1. 점수 증산 (실시간 성간 워프 거리 광년(ly) 미세 누적: 1초당 0.01광년 기본 주행)
        // 부스터 상태(dt에 가속도 기반 반영됨)인 경우 비례하여 더 빠르게 누적됩니다.
        _score.update { it + dt * 0.01f }

        // 내부 균형을 위한 경과 시간(초) 역산 환원
        val timeSpent = _score.value / 0.01f

        // 2. 난이도 가중치: 시간이 경과할수록 유성과 적기의 속도가 훨씬 비례하여 상승 (속도 증가 체감이 더 확실하도록 증진 조율)
        val difficultyMultiplier = 1f + (timeSpent * 0.028f).coerceAtMost(4.5f)
        val spaceWarpSpeed = 200f * difficultyMultiplier

        // 3. 폭풍 회전 복구 수렴
        _shipRoll.update { it * 0.92f }
        _shipPitch.update { it * 0.92f }

        // 4. 폭탄 충전 게이지 (기체의 가속/부스팅과 무관하게 실시간 15초마다 1회 충전)
        _bombGauge.update { gauge ->
            val nextGauge = gauge + (dtRaw / 15f) // 하이퍼 드라이브 가속 등과 무관하게 정직한 리얼타임 15초 기준 누계
            if (nextGauge >= 1.0f) {
                _bombCount.update { it + 1 } // 폭탄 추가
                0f // 초기화
            } else {
                nextGauge
            }
        }

        // 4.5 전술 3D 아이템 주기적 스폰 트리거 (12초마다 충전기, 크로노, 폭탄 중 하나 스폰)
        if (timeSpent >= lastItemSpawnScore + 12f) {
            lastItemSpawnScore = (timeSpent / 12f).toInt() * 12f
            spawnStrategicItem()
        }

        // 5. 쇼크웨이브 폭탄 파장 물리 구동
        if (_shockwaveRadius.value > 0f) {
            _shockwaveRadius.update { radius ->
                val nextRadius = radius + dt * 3500f // 엄청나게 빠른 파동 전파
                if (nextRadius > 2500f) 0f else nextRadius // 소멸
            }
        }

        // 6. 3D 성간 물질 (STARS) 물리 이송
        stars.update { oldStars ->
            oldStars.map { star ->
                val nextZ = star.z - star.speed * difficultyMultiplier * 1.5f * (if (slowActive) 0.5f else 1.0f) * (if (boosting) 1.8f else 1.0f)
                if (nextZ <= 20f) {
                    star.copy(
                        x = (Random.nextFloat() * 3000f - 1500f),
                        y = (Random.nextFloat() * 3000f - 1500f),
                        z = 2400f
                    )
                } else {
                    star.copy(z = nextZ)
                }
            }
        }

        // 6.5 15~30초 랜덤 간격으로 빨간색 적기 스폰 트리거
        if (timeSpent >= lastEnemySpawnScore + nextEnemySpawnInterval) {
            lastEnemySpawnScore = timeSpent
            nextEnemySpawnInterval = Random.nextFloat() * 15f + 15f // 새 랜덤 간격 (15초~30초)
            spawnRedEnemyShip()
        }

        // 7. 3D 유성 (Meteor) 물리 이송 및 원근 크기 변화 & 난이도별 스폰 개수 증가
        val activeShockWave = _shockwaveRadius.value
        val playCrashSensation = mutableListOf<Meteor3D>()
        // 4.5초마다 유성 1개씩 추가 (초기 10개에서 최대 38개까지 속전속결로 사공 소환)
        val targetMeteorCount = 10 + (timeSpent / 4.5f).toInt().coerceAtMost(28)

        meteors.update { oldMeteors ->
            val updatedList = oldMeteors.mapNotNull { meteor ->
                // 유성은 뒤쪽 Z=2500에서 플레이어 쪽 Z=0으로 맹렬 기동
                var nextZ = meteor.z - (350f * meteor.speedFactor * difficultyMultiplier * dtWorld)
                meteor.rotationAngle += meteor.rotationSpeed

                var isDestroyed = false

                // 7-1. 혹시 폭탄 충격파 파장 영역에 피격되었는가? (폭탄 타격 3D 폭발)
                if (activeShockWave > 0f) {
                    // 충격파는 평면 원 형태로 뻗어나감 (플레이어는 z=30에 상주하므로 투영 깊이 내 유역 판정)
                    val dist3D = sqrt(meteor.x * meteor.x + meteor.y * meteor.y + meteor.z * meteor.z)
                    if (dist3D < activeShockWave) {
                        isDestroyed = true
                        triggerExplosion(meteor)
                    }
                }

                // 7-1.5 클린지역 정화 필드 작동 중 (가까이 다가오는 소행성이 무해하게 스타더스트로 소멸)
                if (cleanActive && nextZ <= 1200f) {
                    isDestroyed = true
                    triggerExplosion(meteor, damaged = false)
                }

                // 7-2. 우주선 전방 사정 슬라이스 충돌 판정
                if (!isDestroyed && nextZ <= 100f && nextZ >= 0f) {
                    // 3D 기하 거리 판정 (우주선은 Z=30f 부근에 위치함)
                    val dx = meteor.x - _shipX.value
                    val dy = meteor.y - _shipY.value
                    val dz = nextZ - 30f
                    val distance = sqrt(dx * dx + dy * dy + dz * dz)

                    // 충돌 임계 구체 범주 검증
                    val collisionLimit = meteor.radius + 35f // 35f는 우주선 본체 바운딩 스피어
                    if (distance < collisionLimit) {
                        isDestroyed = true
                        triggerExplosion(meteor, damaged = true)
                        playCrashSensation.add(meteor)
                    }
                }

                when {
                    isDestroyed -> {
                        // 폭사했으므로, 깊은 우주 Z=2500에서 다시 스폰
                        createRandomMeteor()
                    }
                    nextZ <= 10f -> {
                        // 우주선 뒤쪽으로 안전하게 빗겨 날아간 유성
                        createRandomMeteor()
                    }
                    else -> {
                        meteor.copy(z = nextZ)
                    }
                }
            }.toMutableList()

            // 난이도 경과 시간에 따른 유성 개수 보강 (목표 개수 도달할 때까지 소량 부가 스폰)
            while (updatedList.size < targetMeteorCount) {
                updatedList.add(createRandomMeteor(z = 2500f + Random.nextFloat() * 500f))
            }
            updatedList
        }

        // 7.3. 3D 전술 아이템 (Item3D) 물리 기동 및 획득 판정
        items.update { oldItems ->
            oldItems.mapNotNull { item ->
                // 아이템 역시 플레이어 쪽으로 다가옴
                val nextZ = item.z - (300f * item.speedFactor * difficultyMultiplier * dtWorld)
                item.rotationAngle += 4f
                var isCollected = false

                if (nextZ <= 100f && nextZ >= 0f) {
                    val dx = item.x - _shipX.value
                    val dy = item.y - _shipY.value
                    val dz = nextZ - 30f
                    val distance = sqrt(dx * dx + dy * dy + dz * dz)
                    if (distance < (item.radius + 35f)) {
                        isCollected = true
                        applyItemEffect(item.type)
                    }
                }

                when {
                    isCollected -> {
                        triggerItemCollectedExplosion(item)
                        null
                    }
                    nextZ <= 10f -> null
                    else -> item.copy(z = nextZ)
                }
            }
        }

        // 7.5. 3D 빨간색 적기 (EnemyShip3D) 물리 기동 및 복부 충돌 격추 판정
        enemyShips.update { oldShips ->
            oldShips.mapNotNull { enemy ->
                var nextZ = if (enemy.isFromBehind) {
                    // 뒤에서 밀고 들어오는 우주선은 빠른 속도로 우주 전방(Z 증가)으로 지나쳐감
                    enemy.z + (enemy.speed * dtWorld)
                } else {
                    // 앞에서 마주 돌진해 우주선은 맹렬한 기세로 우리 쪽(Z 감소)으로 질주 (배율 상향으로 역동성 극대화)
                    enemy.z - (enemy.speed * difficultyMultiplier * 0.95f * dtWorld)
                }
                
                enemy.rotationAngle += 12f // 기동성 살린 회전 물리 묘사
                var isDestroyed = enemy.isDestroyed
                
                // 폭탄 충격파 사정 피격 파괴 검증
                if (activeShockWave > 0f) {
                    val dist3D = sqrt(enemy.x * enemy.x + enemy.y * enemy.y + enemy.z * enemy.z)
                    if (dist3D < activeShockWave) {
                        isDestroyed = true
                        triggerExplosionForEnemy(enemy)
                    }
                }
                
                // 플레이어 기체와 3D 인접 평면 충돌 판정 (우주선은 Z=30f 부근 위치)
                val zDiff = kotlin.math.abs(nextZ - 30f)
                if (!isDestroyed && zDiff < 45f) {
                    val dx = enemy.x - _shipX.value
                    val dy = enemy.y - _shipY.value
                    val dist2D = sqrt(dx * dx + dy * dy)
                    
                    if (dist2D < (enemy.radius + 35f)) {
                        isDestroyed = true
                        triggerExplosionForEnemy(enemy, damaged = true)
                        playCrashSensation.add(Meteor3D(0L, enemy.x, enemy.y, nextZ, enemy.radius, 0f))
                    }
                }
                
                when {
                    isDestroyed -> null
                    enemy.isFromBehind && nextZ >= 2450f -> null // 원경 돌입 완료 소멸
                    !enemy.isFromBehind && nextZ <= 10f -> null  // 화면 뒤 비행 통과 소멸
                    else -> enemy.copy(z = nextZ, isDestroyed = isDestroyed)
                }
            }
        }

        // 충격 체력(쉴드) 가감 관리 및 게임 오버 판정 트리거
        if (playCrashSensation.isNotEmpty()) {
            _shieldDamageTime.value = System.currentTimeMillis()
            _shield.update { currentShield ->
                val nextShield = currentShield - playCrashSensation.size
                if (nextShield <= 0) {
                    endGame()
                    0
                } else {
                    nextShield
                }
            }
        }

        // 8. 3D 폭발 파티클 물리 기동
        particles.update { oldParticles ->
            oldParticles.mapNotNull { p ->
                val nextLife = p.life - dt * 1.5f
                if (nextLife <= 0f) null
                else {
                    p.copy(
                        x = p.x + p.vx * dt,
                        y = p.y + p.vy * dt,
                        z = p.z + p.vz * dt,
                        life = nextLife
                    )
                }
            }
        }
    }

    // 유성 파괴 시 3D 파티클 파편을 격비있게 활성 분출
    private fun triggerExplosion(meteor: Meteor3D, damaged: Boolean = false) {
        val count = if (damaged) 30 else 15
        val color = if (damaged) 0xFFFF4444.toInt() else when (meteor.colorType) {
            0 -> 0xFFFFCC00.toInt() // 유황별 금빛
            1 -> 0xFF44AAFF.toInt() // 얼음별 오팔빛
            else -> 0xFFFF55AA.toInt() // 오렌지 암석
        }

        val list = mutableListOf<Particle3D>()
        for (i in 0 until count) {
            list.add(
                Particle3D(
                    x = meteor.x,
                    y = meteor.y,
                    z = meteor.z,
                    vx = (Random.nextFloat() * 800f - 400f),
                    vy = (Random.nextFloat() * 800f - 400f),
                    vz = (Random.nextFloat() * 400f - 200f),
                    color = color
                )
            )
        }
        particles.update { it + list }
    }

    // 소형 필살 폭탄 발사 액션 (가시성 높인 버튼 클릭 이벤트 바인딩)
    fun triggerBomb() {
        if (_gameState.value != GameState.PLAYING) return
        if (_bombCount.value <= 0) return

        // 폭탄 대량 전개 격발 소진
        _bombCount.update { it - 1 }

        // 우주선 위치에서부터 퍼져나가는 3D 충격파 발생
        _shockwaveRadius.value = 100f
    }

    // 게임 종료 및 최고 점수 랭킹 Room 영속화
    private fun endGame() {
        _gameState.value = GameState.GAMEOVER
        gameLoopJob?.cancel()

        val finalScore = _score.value
        viewModelScope.launch {
            try {
                scoreRepository.insertScore(finalScore)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // DB 스코어 히스토리 정리
    fun clearScores() {
        viewModelScope.launch {
            scoreRepository.clearScores()
        }
    }

    // 게임 상태 홈화면으로 리셋 (Back 버튼 대응)
    fun resetToReady() {
        _gameState.value = GameState.READY
        gameLoopJob?.cancel()
    }

    private fun spawnRedEnemyShip() {
        val currentList = enemyShips.value.toMutableList()
        val isFromBehind = false // 아군 기체 진행 방향의 정면 반대 방향(원경 Z=2500f)에서 마주 돌진해 날아옴
        val spawnZ = 2500f
        
        // 적 전투기의 무자비한 자동 조정을 분산하여 넓은 방향으로 분산 진격하도록 록온 오차각 범위 조정
        val playerX = _shipX.value
        val playerY = _shipY.value
        val targetX = playerX + (Random.nextFloat() * 540f - 270f)
        val targetY = playerY + (Random.nextFloat() * 540f - 270f)
        
        val enemy = EnemyShip3D(
            id = Random.nextLong(),
            x = targetX,
            y = targetY,
            z = spawnZ,
            speed = 1850f, // 적기 돌진 속도를 1850f로 고속 가쇄하여 극한의 회피 스릴 선사 (기존 1150f)
            isFromBehind = isFromBehind
        )
        currentList.add(enemy)
        enemyShips.value = currentList
    }

    private fun triggerExplosionForEnemy(enemy: EnemyShip3D, damaged: Boolean = false) {
        val count = if (damaged) 40 else 25
        val color = 0xFFFF2222.toInt() // 적기 폭발은 강렬한 네온 적색

        val list = mutableListOf<Particle3D>()
        for (i in 0 until count) {
            list.add(
                Particle3D(
                    x = enemy.x,
                    y = enemy.y,
                    z = enemy.z,
                    vx = (Random.nextFloat() * 1000f - 500f),
                    vy = (Random.nextFloat() * 1000f - 500f),
                    vz = (Random.nextFloat() * 600f - 300f),
                    color = color
                )
            )
        }
        particles.update { it + list }
    }

    private fun spawnStrategicItem() {
        val currentList = items.value.toMutableList()
        // 무작위 전술 타입 (방어막 충전, 시간 지연, 폭탄 가속 배터리, 클린지역 장막 스플래셔)
        // 만약 쉴드가 완충(3개 가득 찬 상태)인 경우에는 쉴드 아이템 스폰을 배제하고 크로노(SLOW), 클린(CLEAN) 또는 폭탄(BOMB) 전술 아이템이 교차 스폰됩니다.
        val type = if (_shield.value >= 3) {
            val rand = Random.nextFloat()
            when {
                rand < 0.45f -> ItemType.SLOW
                rand < 0.90f -> ItemType.CLEAN
                else -> ItemType.BOMB // 폭탄 출현 빈도 소량 감쇄 (10%)
            }
        } else {
            val rand = Random.nextFloat()
            when {
                rand < 0.35f -> ItemType.SHIELD
                rand < 0.65f -> ItemType.SLOW
                rand < 0.90f -> ItemType.CLEAN
                else -> ItemType.BOMB // 폭탄 출현 빈도 소량 감쇄 (10%)
            }
        }

        val paddingX = 160f
        val paddingY = 160f
        val maxX = ((_screenWidth.value / 2f) - paddingX) / 3f
        val maxY = ((_screenHeight.value / 2f) - paddingY) / 3f
        val clampX = maxX.coerceAtLeast(100f)
        val clampY = maxY.coerceAtLeast(100f)

        // 아이템은 플레이어가 기동하여 확실히 획득할 수 있도록 허용 조작 한계영역 안쪽(약 80%)에 이쁘게 안착 스폰
        val spawnX = Random.nextFloat() * (clampX * 1.6f) - (clampX * 0.8f)
        val spawnY = Random.nextFloat() * (clampY * 1.6f) - (clampY * 0.8f)

        val item = Item3D(
            id = Random.nextLong(),
            type = type,
            x = spawnX,
            y = spawnY,
            z = 2500f
        )
        currentList.add(item)
        items.value = currentList
    }

    private fun applyItemEffect(type: ItemType) {
        val message = when (type) {
            ItemType.SHIELD -> {
                // 실드 즉시 1개 충전 (최대치 3 복원 유지)
                _shield.update { (it + 1).coerceAtMost(3) }
                "🛡️ 보호막 회복! 쉴드 내구도 +1 충전 완료!"
            }
            ItemType.SLOW -> {
                // 성간 크로노 타임 슬로우 5초 충적
                _chronoSlowTime.update { it + 5.0f }
                "🌀 크로노 필드! 5초간 주변 소행성/적선 속도 50% 감속!"
            }
            ItemType.BOMB -> {
                // 플라즈마 에너지 즉시 폭탄 +1 추가
                _bombCount.update { it + 1 }
                "⚡ 폭탄 셀 충전! 플라즈마 폭탄 수량 +1 추가!"
            }
            ItemType.CLEAN -> {
                // 3초간 클린 진공지역 정화 장막 활성화
                _cleanTime.update { it + 3.0f }
                "✨ 클린 보이드 필드! 3초간 다가오는 장애물을 완전 정화합니다!"
            }
        }
        
        _itemMessage.value = message
        itemMessageClearJob?.cancel()
        itemMessageClearJob = viewModelScope.launch {
            delay(2500)
            if (_itemMessage.value == message) {
                _itemMessage.value = null
            }
        }
    }

    private fun triggerItemCollectedExplosion(item: Item3D) {
        val color = when (item.type) {
            ItemType.SHIELD -> 0xFF10B981.toInt() // 쉴드 획득 시 영롱한 녹색 펄 파편
            ItemType.SLOW -> 0xFF06B6D4.toInt()   // 감속 획득 시 시안 블루 크리스탈 파편
            ItemType.BOMB -> 0xFFC084FC.toInt()   // 폭탄 배터리 획득 시 보라색 전자기적 스파크
            ItemType.CLEAN -> 0xFFFCD34D.toInt()  // 클린지역 웅장한 골든 스타 파편
        }
        val list = mutableListOf<Particle3D>()
        for (i in 0 until 22) {
            list.add(
                Particle3D(
                    x = item.x,
                    y = item.y,
                    z = item.z,
                    vx = (Random.nextFloat() * 600f - 300f),
                    vy = (Random.nextFloat() * 600f - 300f),
                    vz = (Random.nextFloat() * 300f - 150f),
                    color = color
                )
            )
        }
        particles.update { it + list }
    }

    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
    }
}
