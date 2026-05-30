package com.example.game

import android.os.Build
import android.os.Vibrator
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import com.example.data.ScoreEntity
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }

    // State flows 구독
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val score by viewModel.score.collectAsStateWithLifecycle()
    val shield by viewModel.shield.collectAsStateWithLifecycle()
    val bombCount by viewModel.bombCount.collectAsStateWithLifecycle()
    val bombGauge by viewModel.bombGauge.collectAsStateWithLifecycle()
    val shieldDamageTime by viewModel.shieldDamageTime.collectAsStateWithLifecycle()
    val isBoosting by viewModel.isBoosting.collectAsStateWithLifecycle()
    val isNearBorder by viewModel.isNearBorder.collectAsStateWithLifecycle()
    val boostPitchOffset by animateFloatAsState(
        targetValue = if (isBoosting) -22f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "boost_pitch"
    )

    // 시스템 Back 버튼 터치 시 무조건 홈화면(READY 상태)으로 돌아가도록 유도
    BackHandler(enabled = gameState != GameState.READY) {
        viewModel.resetToReady()
    }

    val shipX by viewModel.shipX.collectAsStateWithLifecycle()
    val shipY by viewModel.shipY.collectAsStateWithLifecycle()
    val shipRoll by viewModel.shipRoll.collectAsStateWithLifecycle()
    val shipPitch by viewModel.shipPitch.collectAsStateWithLifecycle()

    val stars by viewModel.stars.collectAsStateWithLifecycle()
    val meteors by viewModel.meteors.collectAsStateWithLifecycle()
    val enemyShips by viewModel.enemyShips.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val chronoSlowTime by viewModel.chronoSlowTime.collectAsStateWithLifecycle()
    val cleanTime by viewModel.cleanTime.collectAsStateWithLifecycle()
    val itemMessage by viewModel.itemMessage.collectAsStateWithLifecycle()
    val particles by viewModel.particles.collectAsStateWithLifecycle()
    val shockwaveRadius by viewModel.shockwaveRadius.collectAsStateWithLifecycle()

    val highScoreEntity by viewModel.highScoreState.collectAsStateWithLifecycle()
    val allScores by viewModel.allScoresState.collectAsStateWithLifecycle()

    var showScoreHistoryDialog by remember { mutableStateOf(false) }
    var showControlsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // 쉴드 타격 진동 피드백 및 화면 가벼운 흔들림 유도
    LaunchedEffect(shieldDamageTime) {
        if (shieldDamageTime > 0L) {
            try {
                // 안드로이드 햅틱 진동
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(120, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(120)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 3D 셰이딩 광원에 쓰일 시각 회전용 애니메이션 각도값
    val transition = rememberInfiniteTransition(label = "core_glow")
    val backgroundPulseAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bg_pulse"
    )
    val borderFlashAlpha by transition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_flash"
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        containerColor = Color(0xFF020215) // 심우주 완전한 어두움
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- 1. 우주 3D 시물레이터 입체 캔버스 영역 ---
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("game_field")
                    .onSizeChanged { size ->
                        viewModel.updateScreenBounds(size.width.toFloat(), size.height.toFloat())
                    }
                    .pointerInput(gameState) {
                        if (gameState == GameState.PLAYING) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressedPointers = event.changes.filter { it.pressed }
                                    val pointerCount = pressedPointers.size

                                    // 2개 이상의 터치(더블 터치 / 멀티 터치)가 들어오면 하이퍼 부스트 가속 온!
                                    viewModel.setBoosting(pointerCount >= 2)

                                    // 첫 번째 터치 포인트의 드래그 차이값으로 우주선을 실시간 이동 조작
                                    val dragChange = event.changes.firstOrNull { it.id == event.changes.firstOrNull()?.id }
                                    if (dragChange != null && dragChange.pressed) {
                                        val diff = dragChange.position - dragChange.previousPosition
                                        if (diff != Offset.Zero) {
                                            dragChange.consume()
                                            viewModel.moveShip(diff.x, diff.y)
                                        }
                                    }

                                    // 화면에서 모든 손가락을 떼면 자세 안정화 및 부스팅 해제
                                    val isAnyPressed = event.changes.any { it.pressed }
                                    if (!isAnyPressed) {
                                        viewModel.stabilizeShip()
                                        viewModel.setBoosting(false)
                                    }
                                }
                            }
                        }
                    }
            ) {
                val width = size.width
                val height = size.height
                val centerX = width / 2f
                val centerY = height / 2f

                // 우주선 쉴드가 무너지면 화면 흔들리는 3D 격동 효과 구현 (Damage Shake)
                val isShaking = System.currentTimeMillis() - shieldDamageTime < 300L
                val offsetX = if (isShaking) (Math.random() * 30 - 15).toFloat() else 0f
                val offsetY = if (isShaking) (Math.random() * 30 - 15).toFloat() else 0f

                // (A) 성간 깊이감 3D 방사 그라데이션 배경 및 네뷸라 연출 (Sleek Interface: #1e1b4b & #020617)
                drawRect(color = Color(0xFF020617), size = size)

                // 중심부 네뷸라 가스 그라데이션 커스텀 효과
                val nebulaGradient1 = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E1B4B).copy(alpha = 0.5f), // 심우주 플라즈마 퍼플
                        Color.Transparent
                    ),
                    center = Offset(centerX * 0.8f + offsetX, centerY * 0.9f + offsetY),
                    radius = width * 0.8f
                )
                drawRect(brush = nebulaGradient1, size = size)

                val nebulaGradient2 = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1D4ED8).copy(alpha = 0.15f), // 블루 은하 광원
                        Color.Transparent
                    ),
                    center = Offset(centerX * 1.3f + offsetX, centerY * 1.2f + offsetY),
                    radius = width * 0.7f
                )
                drawRect(brush = nebulaGradient2, size = size)

                // (B) 3D 입체 성간 물질 (STARS) 렌더링
                stars.forEach { star ->
                    // 3D Perspective Projection Matrix
                    val focalLength = 600f
                    val projectedX = centerX + star.x * (focalLength / star.z) + offsetX
                    val projectedY = centerY + star.y * (focalLength / star.z) + offsetY

                    // 화면 범위 내에 투영되는 물체들만 가시 드로잉
                    if (projectedX in 0f..width && projectedY in 0f..height) {
                        // 가까이 올수록 입자의 지름이 커지며 속도감이 나타납니다.
                        val sizeMultiplier = (1.0f - (star.z / 2400f)).coerceIn(0.1f, 1.0f)
                        val radius = 1.0f + 5.0f * sizeMultiplier
                        val opacity = (star.z / 2400f).coerceIn(0.1f, 0.9f) // 꼬리가 가깝게 빛남

                        drawCircle(
                            color = Color.White.copy(alpha = opacity),
                            radius = radius,
                            center = Offset(projectedX, projectedY)
                        )
                    }
                }

                // (C) 대폭발 폭탄 쇼크웨이브 방사 3D 링
                if (shockwaveRadius >= 20f) {
                    val pulseAlpha = (1.0f - (shockwaveRadius / 2500f)).coerceIn(0f, 1f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x33FF6600),
                                Color(0xFFFF5500).copy(alpha = pulseAlpha * 0.9f),
                                Color.Transparent
                            ),
                            center = Offset(centerX + offsetX, centerY + offsetY),
                            radius = shockwaveRadius
                        ),
                        radius = shockwaveRadius,
                        center = Offset(centerX + offsetX, centerY + offsetY),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 45f)
                    )
                }

                // (D) 3D 입체 크레이터 셰이딩 유성 (METEORS) 렌더링
                meteors.forEach { meteor ->
                    val focalLength = 600f
                    val projX = centerX + meteor.x * (focalLength / meteor.z) + offsetX
                    val projY = centerY + meteor.y * (focalLength / meteor.z) + offsetY

                    // 원근에 따른 구체 그래픽스 직경 크기 보간
                    val projectedRadius = meteor.radius * (focalLength / meteor.z)

                    if (projX in -projectedRadius..width + projectedRadius &&
                        projY in -projectedRadius..height + projectedRadius
                    ) {
                        // 3D 암석 질감을 표현하기 위해 입체 래디얼 음영 구현
                        val colorTheme = when (meteor.colorType) {
                            0 -> listOf(Color(0xFFFFEE66), Color(0xFFD4AF37), Color(0xFF5A450C)) // 골드 메탈릭
                            1 -> listOf(Color(0xFF88DFFF), Color(0xFF4298B5), Color(0xFF0F3E4D)) // 청화 구형
                            else -> listOf(Color(0xFFFF8888), Color(0xFFE94560), Color(0xFF532431)) // 마젠타 코멧
                        }

                        // 광원의 위치를 (우측 상단)에 치우쳐 입체 음영 형성
                        val lightCenter = Offset(projX - projectedRadius * 0.25f, projY - projectedRadius * 0.25f)

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = colorTheme,
                                center = lightCenter,
                                radius = projectedRadius
                            ),
                            radius = projectedRadius,
                            center = Offset(projX, projY)
                        )

                        // 3D 원근 위성/크레이터 회전 구형 디테일 시뮬레이션
                        val moonAngleRad = Math.toRadians(meteor.rotationAngle.toDouble())
                        val crX = projX + (projectedRadius * 0.4f * cos(moonAngleRad)).toFloat()
                        val crY = projY + (projectedRadius * 0.4f * sin(moonAngleRad)).toFloat()

                        drawCircle(
                            color = Color.Black.copy(alpha = 0.5f),
                            radius = projectedRadius * 0.2f,
                            center = Offset(crX, crY)
                        )
                    }
                }

                // (D-2) 3D 빨간색 적기 (ENEMY_SHIPS) 렌더링
                enemyShips.forEach { enemy ->
                    val focalLength = 600f
                    val projX = centerX + enemy.x * (focalLength / enemy.z) + offsetX
                    val projY = centerY + enemy.y * (focalLength / enemy.z) + offsetY

                    // 원근에 따른 구체/기체 크기 보간
                    val projectedRadius = enemy.radius * (focalLength / enemy.z)

                    if (projX in -projectedRadius..width + projectedRadius &&
                        projY in -projectedRadius..height + projectedRadius &&
                        enemy.z > 0f
                    ) {
                        // 3D 적기 형태 그리기 - 회전 각도를 엄밀히 투영하는 SF 전술 전투기 (Advanced SF Starfighter Visuals)
                        val angleRad = Math.toRadians(enemy.rotationAngle.toDouble())
                        val cosVal = cos(angleRad).toFloat()
                        val sinVal = sin(angleRad).toFloat()

                        // 로컬 좌표계를 회전시켜 정확히 배치하는 헬퍼 함수
                        fun rx(lx: Float, ly: Float): Float = projX + (lx * cosVal - ly * sinVal)
                        fun ry(lx: Float, ly: Float): Float = projY + (lx * sinVal + ly * cosVal)

                        // 1. 입체 외장 갑피 다각형 패스 (Main Wing & Armor Shell Path)
                        val hullPath = Path().apply {
                            val nose = Offset(rx(0f, -projectedRadius * 1.6f), ry(0f, -projectedRadius * 1.6f))
                            val leftWingTip = Offset(rx(-projectedRadius * 1.4f, projectedRadius * 0.4f), ry(-projectedRadius * 1.4f, projectedRadius * 0.4f))
                            val leftFlap = Offset(rx(-projectedRadius * 0.5f, projectedRadius * 0.2f), ry(-projectedRadius * 0.5f, projectedRadius * 0.2f))
                            val rearPowerCore = Offset(rx(0f, projectedRadius * 0.7f), ry(0f, projectedRadius * 0.7f))
                            val rightFlap = Offset(rx(projectedRadius * 0.5f, projectedRadius * 0.2f), ry(projectedRadius * 0.5f, projectedRadius * 0.2f))
                            val rightWingTip = Offset(rx(projectedRadius * 1.4f, projectedRadius * 0.4f), ry(projectedRadius * 1.4f, projectedRadius * 0.4f))

                            moveTo(nose.x, nose.y)
                            lineTo(leftWingTip.x, leftWingTip.y)
                            lineTo(leftFlap.x, leftFlap.y)
                            lineTo(rearPowerCore.x, rearPowerCore.y)
                            lineTo(rightFlap.x, rightFlap.y)
                            lineTo(rightWingTip.x, rightWingTip.y)
                            close()
                        }

                        // 2. 어둡고 위협적인 레드 그라디언트 기본 갑피 렌더링
                        drawPath(
                            path = hullPath,
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFF1E30), Color(0xFFDC2626), Color(0xFF7F1D1D), Color(0xFF1E0002)),
                                center = Offset(projX, projY),
                                radius = projectedRadius * 1.6f
                            ),
                            alpha = 0.98f
                        )

                        // 3. 네온 오렌지 가늠 도금선 선처리 (Neon Structural Edge Lines)
                        drawPath(
                            path = hullPath,
                            color = Color(0xFFF97316),
                            style = Stroke(width = (projectedRadius * 0.08f).coerceAtLeast(1.8f)),
                            alpha = 0.9f
                        )

                        // 4. 고출력 트윈 제트 플라즈마 엔진 분화염 (Dual Plasma Thruster Flames)
                        val leftThrustCenter = Offset(rx(-projectedRadius * 0.4f, projectedRadius * 0.6f), ry(-projectedRadius * 0.4f, projectedRadius * 0.6f))
                        val rightThrustCenter = Offset(rx(projectedRadius * 0.4f, projectedRadius * 0.6f), ry(projectedRadius * 0.4f, projectedRadius * 0.6f))
                        val flameRadius = projectedRadius * (0.6f + 0.25f * sin(System.currentTimeMillis() * 0.06f).toFloat())

                        // 좌측 제트 불꽃
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFEE2E2), Color(0xFFEF4444), Color(0xFF7F1D1D), Color.Transparent),
                                center = leftThrustCenter,
                                radius = flameRadius
                            ),
                            radius = flameRadius,
                            center = leftThrustCenter,
                            alpha = 0.95f
                        )
                        // 우측 제트 불꽃
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFEE2E2), Color(0xFFEF4444), Color(0xFF7F1D1D), Color.Transparent),
                                center = rightThrustCenter,
                                radius = flameRadius
                            ),
                            radius = flameRadius,
                            center = rightThrustCenter,
                            alpha = 0.95f
                        )

                        // 5. 양쪽 날개 끝 주포 에너지 충전등 (Decal Wingtip Charged Cannons)
                        val leftCannon = Offset(rx(-projectedRadius * 1.35f, projectedRadius * 0.3f), ry(-projectedRadius * 1.35f, projectedRadius * 0.3f))
                        val rightCannon = Offset(rx(projectedRadius * 1.35f, projectedRadius * 0.3f), ry(projectedRadius * 1.35f, projectedRadius * 0.3f))
                        val cannonSightGlow = projectedRadius * 0.25f

                        // 좌측 포탑 충전구 그리기
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFFFFFF), Color(0xFFF59E0B), Color.Transparent),
                                center = leftCannon,
                                radius = cannonSightGlow
                            ),
                            radius = cannonSightGlow,
                            center = leftCannon
                        )
                        // 우측 포탑 충전구 그리기
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFFFFFF), Color(0xFFF59E0B), Color.Transparent),
                                center = rightCannon,
                                radius = cannonSightGlow
                            ),
                            radius = cannonSightGlow,
                            center = rightCannon
                        )

                        // 6. 지능형 메인 위협 눈빛 조종석 쉴드 (Advanced Core Visor Canopy & Targeting Crosshair)
                        val visorCenter = Offset(rx(0f, -projectedRadius * 0.2f), ry(0f, -projectedRadius * 0.2f))
                        val visorRadius = projectedRadius * 0.4f

                        // 가로 지르는 기계식 바이저 밴드
                        drawLine(
                            color = Color(0xFF7F1D1D),
                            start = Offset(rx(-projectedRadius * 0.8f, -projectedRadius * 0.2f), ry(-projectedRadius * 0.8f, -projectedRadius * 0.2f)),
                            end = Offset(rx(projectedRadius * 0.8f, -projectedRadius * 0.2f), ry(projectedRadius * 0.8f, -projectedRadius * 0.2f)),
                            strokeWidth = (projectedRadius * 0.12f).coerceAtLeast(2f)
                        )

                        // 육각형 홀로그램 위협 안광
                        drawCircle(
                            color = Color(0xFF1E293B),
                            radius = visorRadius,
                            center = visorCenter
                        )
                        // 강렬하게 빚어내는 타겟팅 레이저 코어 (붉은 심장)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFFFFFF), Color(0xFFFF0033), Color(0xFF450A0A)),
                                center = visorCenter,
                                radius = visorRadius * 0.7f
                            ),
                            radius = visorRadius * 0.7f,
                            center = visorCenter
                        )
                        drawCircle(
                            color = Color(0xFFFCA5A5),
                            radius = visorRadius * 0.25f,
                            center = visorCenter
                        )
                    }
                }

                // (D-3) 3D 전술 기능성 아이템 (Strategic Items - Shield/Slow/Bomb) 렌더링
                items.forEach { item ->
                    val focalLength = 600f
                    val projX = centerX + item.x * (focalLength / item.z) + offsetX
                    val projY = centerY + item.y * (focalLength / item.z) + offsetY

                    // 원근 보간용 반지름
                    val projectedRadius = item.radius * (focalLength / item.z)

                    if (projX in -projectedRadius..width + projectedRadius &&
                        projY in -projectedRadius..height + projectedRadius &&
                        item.z > 0f
                    ) {
                        // 아이템 고유 색상 및 아이콘 결정
                        val colorTheme = when (item.type) {
                            ItemType.SHIELD -> listOf(Color(0xFF86EFAC), Color(0xFF10B981), Color(0xFF065F46).copy(alpha = 0.3f)) // Emerald Green Gradient
                            ItemType.SLOW -> listOf(Color(0xFF67E8F9), Color(0xFF06B6D4), Color(0xFF0E7490).copy(alpha = 0.3f))   // Cyan Hourglass Gradient
                            ItemType.BOMB -> listOf(Color(0xFFD8B4FE), Color(0xFFC084FC), Color(0xFF6B21A8).copy(alpha = 0.3f))   // Purple Cell Battery Gradient
                            ItemType.CLEAN -> listOf(Color(0xFFFDE047), Color(0xFFF59E0B), Color(0xFF78350F).copy(alpha = 0.3f))  // Golden Star Clean Gradient
                        }
                        val symbol = when (item.type) {
                            ItemType.SHIELD -> "🛡️"
                            ItemType.SLOW -> "🌀"
                            ItemType.BOMB -> "⚡"
                            ItemType.CLEAN -> "✨"
                        }

                        // 맥동(회전/발광) 효과
                        val itemTimer = System.currentTimeMillis() * 0.005f
                        val pulsateR = projectedRadius * (1.1f + 0.12f * sin(itemTimer).toFloat())

                        // 1. 아우라 잔광 드로잉 (Glow Halo)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(colorTheme[1].copy(alpha = 0.35f), Color.Transparent),
                                center = Offset(projX, projY),
                                radius = pulsateR * 1.8f
                            ),
                            radius = pulsateR * 1.8f,
                            center = Offset(projX, projY)
                        )

                        // 2. 외부 테두리 보호구 고리 드로잉 (Neon Tech Outer Circle)
                        drawCircle(
                            color = colorTheme[0],
                            radius = pulsateR,
                            center = Offset(projX, projY),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = (projectedRadius * 0.15f).coerceAtLeast(1.5f))
                        )

                        // 3. 중심 내부 구체 드로잉 (Tactical Shiny Core)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = colorTheme,
                                center = Offset(projX - pulsateR * 0.15f, projY - pulsateR * 0.15f),
                                radius = pulsateR * 0.85f
                            ),
                            radius = pulsateR * 0.85f,
                            center = Offset(projX, projY)
                        )

                        // 4. 중앙 전술 이정부 심볼 아이콘 텍스트 렌더링 (Native Font Shading Draw)
                        val textPaint = android.graphics.Paint().apply {
                            textSize = pulsateR * 0.95f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            symbol,
                            projX,
                            projY + (pulsateR * 0.33f),
                            textPaint
                        )
                    }
                }

                // (E) 폭사 파티클 이펙트
                particles.forEach { p ->
                    val focalLength = 600f
                    val px = centerX + p.x * (focalLength / p.z) + offsetX
                    val py = centerY + p.y * (focalLength / p.z) + offsetY
                    val pSize = 10f * (focalLength / p.z) * p.life

                    if (px in 0f..width && py in 0f..height) {
                        drawCircle(
                            color = Color(p.color).copy(alpha = p.life),
                            radius = pSize.coerceAtLeast(1f),
                            center = Offset(px, py)
                        )
                    }
                }

                // (F) 기동하는 3D 초시공 전투 우주선 (SPACESHIP 3D ENGINE - High Fidelity Starfighter Redesign)
                if (gameState == GameState.PLAYING) {
                    val shipProjZ = 200f // 우주선 기준 3D 가상 깊이
                    val shipPosX = shipX
                    val shipPosY = shipY

                    // 1. 우주선의 기하 3D 폴리곤 정점 데이터 세팅 (X, Y, Z) - 3D Starfighter Model
                    val vertices3D = listOf(
                        Point3D(0f, -50f, 0f),      // 0: Cockpit Nose (정코)
                        Point3D(-14f, 10f, 5f),     // 1: Body Left (좌측 동체 부피)
                        Point3D(14f, 10f, 5f),      // 2: Body Right (우측 동체 부피)
                        Point3D(0f, -12f, -18f),    // 3: Cockpit Canopy (올라온 3D 조종석 탑)
                        Point3D(0f, 30f, 8f),       // 4: Engine Center (동체 정중앙 하단 배출구)
                        Point3D(-52f, 22f, 12f),    // 5: Left Wingtip (좌측 날개 끝)
                        Point3D(-14f, 26f, 5f),     // 6: Left Wing Joint Rear (좌측 날개 후방 접합)
                        Point3D(52f, 22f, 12f),     // 7: Right Wingtip (우측 날개 끝)
                        Point3D(14f, 26f, 5f),      // 8: Right Wing Joint Rear (우측 날개 후방 접합)
                        Point3D(0f, 20f, -28f),     // 9: Vertical Fin Top (수직 미익 고점)
                        Point3D(-8f, 32f, 0f),      // 10: Left Engine Core (좌측 듀얼 제트 엔진 코어)
                        Point3D(8f, 32f, 0f)        // 11: Right Engine Core (우측 듀얼 제트 엔진 코어)
                    )

                    // 2. 사용자의 드래그 기울기(shipRoll, shipPitch) 정보에 의한 회전행렬 연산 후 투영 (기본 피치 -38도를 주어 꽁지 추진 노즐이 정면(사용자 쪽)으로 향하고 기수가 스크린 안으로 빨려드는 구도 설정, 가속 시 기체가 앞쪽으로 더 기울어지도록 boostPitchOffset 결합)
                    val rollRad = Math.toRadians(shipRoll.toDouble()).toFloat()
                    val pitchRad = Math.toRadians((shipPitch - 38f + boostPitchOffset).toDouble()).toFloat()

                    val projected2DList = vertices3D.map { pt ->
                        // (Roll 회전) - Z축 기준 회전
                        val x1 = pt.x * cos(rollRad) - pt.y * sin(rollRad)
                        val y1 = pt.x * sin(rollRad) + pt.y * cos(rollRad)
                        val z1 = pt.z

                        // (Pitch 회전) - X축 기준 회전
                        val x2 = x1
                        val y2 = y1 * cos(pitchRad) - z1 * sin(pitchRad)
                        val z2 = y1 * sin(pitchRad) + z1 * cos(pitchRad)

                        // 3D 위치 반영
                        val final3D = Point3D(x2 + shipPosX, y2 + shipPosY, z2 + shipProjZ)

                        // 원근 정투영 적용
                        val focal = 600f
                        val screenU = centerX + final3D.x * (focal / final3D.z)
                        val screenV = centerY + final3D.y * (focal / final3D.z)
                        Offset(screenU, screenV)
                    }

                    // 2D 폴리곤 패스 연결 구성 (Painter's Algorithm: Back elements first)
                    // Left Wing (Two triangular pieces for 3D sweep)
                    val leftWingFrontPath = Path().apply {
                        moveTo(projected2DList[0].x, projected2DList[0].y) // Nose
                        lineTo(projected2DList[5].x, projected2DList[5].y) // Left Wingtip
                        lineTo(projected2DList[1].x, projected2DList[1].y) // Body Left
                        close()
                    }
                    val leftWingRearPath = Path().apply {
                        moveTo(projected2DList[1].x, projected2DList[1].y) // Body Left
                        lineTo(projected2DList[5].x, projected2DList[5].y) // Left Wingtip
                        lineTo(projected2DList[6].x, projected2DList[6].y) // Left Wing Joint Rear
                        close()
                    }

                    // Right Wing
                    val rightWingFrontPath = Path().apply {
                        moveTo(projected2DList[0].x, projected2DList[0].y) // Nose
                        lineTo(projected2DList[7].x, projected2DList[7].y) // Right Wingtip
                        lineTo(projected2DList[2].x, projected2DList[2].y) // Body Right
                        close()
                    }
                    val rightWingRearPath = Path().apply {
                        moveTo(projected2DList[2].x, projected2DList[2].y) // Body Right
                        lineTo(projected2DList[7].x, projected2DList[7].y) // Right Wingtip
                        lineTo(projected2DList[8].x, projected2DList[8].y) // Right Wing Joint Rear
                        close()
                    }

                    // Main Fuselage Left Side
                    val fuselageLeftPath = Path().apply {
                        moveTo(projected2DList[1].x, projected2DList[1].y) // Body Left
                        lineTo(projected2DList[6].x, projected2DList[6].y) // Left Wing Joint Rear
                        lineTo(projected2DList[4].x, projected2DList[4].y) // Engine Center
                        close()
                    }

                    // Main Fuselage Right Side
                    val fuselageRightPath = Path().apply {
                        moveTo(projected2DList[2].x, projected2DList[2].y) // Body Right
                        lineTo(projected2DList[8].x, projected2DList[8].y) // Right Wing Joint Rear
                        lineTo(projected2DList[4].x, projected2DList[4].y) // Engine Center
                        close()
                    }

                    // Vertical Tail Fin
                    val verticalFinPath = Path().apply {
                        moveTo(projected2DList[3].x, projected2DList[3].y) // Cockpit Canopy
                        lineTo(projected2DList[9].x, projected2DList[9].y) // Tail Fin Top
                        lineTo(projected2DList[4].x, projected2DList[4].y) // Engine Center
                        close()
                    }

                    // Cockpit Canopy Core
                    val canopyLeftPath = Path().apply {
                        moveTo(projected2DList[0].x, projected2DList[0].y) // Nose
                        lineTo(projected2DList[1].x, projected2DList[1].y) // Body Left
                        lineTo(projected2DList[3].x, projected2DList[3].y) // Canopy
                        close()
                    }
                    val canopyRightPath = Path().apply {
                        moveTo(projected2DList[0].x, projected2DList[0].y) // Nose
                        lineTo(projected2DList[2].x, projected2DList[2].y) // Body Right
                        lineTo(projected2DList[3].x, projected2DList[3].y) // Canopy
                        close()
                    }

                    // 쉴드 데미지 충격 발생 시 적색 네온 강조, 평소에는 크롬 메탈릭 앤 플래티넘 도색
                    val bodyBrush = if (isShaking) {
                        Brush.verticalGradient(listOf(Color(0xFFFF4D4D), Color(0xFFB71C1C)))
                    } else {
                        Brush.verticalGradient(listOf(Color(0xFFE2E8F0), Color(0xFF64748B))) // Titanium Slate Base
                    }

                    val wingBrush = if (isShaking) {
                        Brush.verticalGradient(listOf(Color(0xFFFF1744), Color(0xFF880E4F)))
                    } else {
                        Brush.verticalGradient(listOf(Color(0xFF38BDF8), Color(0xFF0F172A))) // Deep Blue cyber gloss wings
                    }

                    val canopyBrush = if (isShaking) {
                        Brush.verticalGradient(listOf(Color(0xFFFF8A80), Color(0xFFC62828)))
                    } else {
                        Brush.verticalGradient(listOf(Color(0xFF22D3EE), Color(0xFF0369A1))) // Glowing Cyan Glass Canopy
                    }

                    // 1. Draw Wings (Left & Right)
                    drawPath(path = leftWingFrontPath, brush = wingBrush, alpha = 0.9f)
                    drawPath(path = leftWingRearPath, brush = wingBrush, alpha = 0.85f)
                    drawPath(path = rightWingFrontPath, brush = wingBrush, alpha = 0.9f)
                    drawPath(path = rightWingRearPath, brush = wingBrush, alpha = 0.85f)

                    // 2. Draw Body Fuselage Underside
                    drawPath(path = fuselageLeftPath, brush = bodyBrush, alpha = 0.95f)
                    drawPath(path = fuselageRightPath, brush = bodyBrush, alpha = 0.95f)

                    // 3. Draw Vertical tail fin
                    drawPath(path = verticalFinPath, brush = bodyBrush, alpha = 0.95f)

                    // 4. Draw Cockpit Canopy
                    drawPath(path = canopyLeftPath, brush = canopyBrush, alpha = 0.98f)
                    drawPath(path = canopyRightPath, brush = canopyBrush, alpha = 0.98f)

                    // 윤곽선 드로잉하여 메탈 와이어 프레임 크롬 반사 보완 및 입체각 입히기
                    val wireColor = if (isShaking) Color(0xFFFF8A80) else Color(0xCCFFFFFF)
                    drawPath(path = leftWingFrontPath, color = wireColor.copy(alpha = 0.3f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                    drawPath(path = leftWingRearPath, color = wireColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.0f))
                    drawPath(path = rightWingFrontPath, color = wireColor.copy(alpha = 0.3f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                    drawPath(path = rightWingRearPath, color = wireColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.0f))
                    drawPath(path = fuselageLeftPath, color = wireColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f))
                    drawPath(path = fuselageRightPath, color = wireColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f))
                    drawPath(path = verticalFinPath, color = wireColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.0f))
                    drawPath(path = canopyLeftPath, color = Color.White.copy(alpha = 0.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f))
                    drawPath(path = canopyRightPath, color = Color.White.copy(alpha = 0.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f))

                    // 5. 듀얼 엔진 플라즈마 제트 추진 불꽃 (Dual-core Engine Flames)
                    val flamePulse = (10f * sin(System.currentTimeMillis() * 0.1f)).toFloat()
                    val flameLength = if (isBoosting) 80f else 42f // 부스트 모드일 때 훨씬 길고 웅장하게 분출
                    
                    val leftFlamePath = Path().apply {
                        moveTo(projected2DList[10].x - 6f, projected2DList[10].y)
                        lineTo(projected2DList[10].x, projected2DList[10].y + flameLength + flamePulse)
                        lineTo(projected2DList[10].x + 6f, projected2DList[10].y)
                        close()
                    }
                    val rightFlamePath = Path().apply {
                        moveTo(projected2DList[11].x - 6f, projected2DList[11].y)
                        lineTo(projected2DList[11].x, projected2DList[11].y + flameLength + flamePulse)
                        lineTo(projected2DList[11].x + 6f, projected2DList[11].y)
                        close()
                    }

                    val flameBrush = if (isBoosting) {
                        // 가속 모드: 맹렬한 하이파이어 레드-오렌지 배기 불꽃
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFFFF3D00), Color(0xFFFF9100), Color.Transparent)
                        )
                    } else {
                        // 일상 모드: 은하 플라즈마 블루 배기 필터
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF22D3EE), Color(0x3B82F600), Color.Transparent)
                        )
                    }

                    drawPath(path = leftFlamePath, brush = flameBrush, alpha = 0.95f)
                    drawPath(path = rightFlamePath, brush = flameBrush, alpha = 0.95f)
                }

                // 부딪혔을 때 화면 전체에 옅은 붉은 진동 빛 플래시 피드백 드로잉
                if (isShaking) {
                    drawRect(
                        color = Color(0x22EF4444), // 격조있고 화려한 13% 알파 네온레드 필름 필터
                        size = size
                    )
                }
            }

            // --- 2. 우주선 생존 상태 정보 HUD 계기판 (Sleek Interface 탑 헤더 최적화) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // 현재 비행 거리 (Flight Distance - Split Typography)
                    Column {
                        Text(
                            text = "WARP DISTANCE (ly)",
                            color = Color(0xFF94A3B8), // Sleek slate text
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.Bottom
                        ) {
                            val distanceLy = score
                            val integerPart = distanceLy.toInt()
                            val decimals = ((distanceLy - integerPart) * 100).toInt().coerceIn(0, 99)

                            Text(
                                text = String.format(Locale.getDefault(), "%,d", integerPart),
                                color = Color.White,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Light, // Sleek Light typography
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.testTag("score_display")
                            )
                            Text(
                                text = String.format(Locale.getDefault(), ".%02d ly", decimals),
                                color = Color(0xFF38BDF8), // Cyan accents
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Light,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                            )
                        }
                    }

                    // 퍼스널 베스트 (Personal Best / High Score)
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "PERSONAL BEST",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.5.sp,
                            textAlign = TextAlign.End
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        highScoreEntity?.let {
                            val bestDistance = it.scoreValue
                            val bestInteger = bestDistance.toInt()
                            val bestDec = ((bestDistance - bestInteger) * 100).toInt().coerceIn(0, 99)

                            Text(
                                text = String.format(Locale.getDefault(), "%,d.%02d ly", bestInteger, bestDec),
                                color = Color(0xFFCBD5E1),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                        } ?: Text(
                            text = "0.00 ly",
                            color = Color(0xFF64748B),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 쉴드 라이브 상태 슬림 바 (SHIELD SYSTEM)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SHIELD",
                                color = Color(0xFFEF4444),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            for (i in 1..3) {
                                Box(
                                    modifier = Modifier
                                        .size(height = 6.dp, width = 16.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(
                                            if (i <= shield) Color(0xFFEF4444) else Color(0x33EF4444)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // --- 2.5 화면 가장자리 근접 경고 (경계선 이탈 방지용 실시간 수렴 오버레이) ---
            if (gameState == GameState.PLAYING && isNearBorder) {
                // 화면 보더 붉은 그라데이션 맥박형 발광 광채
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 6.dp,
                            brush = SolidColor(Color(0xFFEF4444).copy(alpha = borderFlashAlpha)),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFFEF4444).copy(alpha = borderFlashAlpha * 0.22f)
                                ),
                                radius = 2200f
                            )
                        )
                )

                // 화면 상단에 "COLLISION WARNING" 가시적 알림 바 오버레이
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 110.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(Color(0xE67F1D1D), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .border(1.dp, Color(0xFFF87171), shape = RoundedCornerShape(8.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Text(
                            text = "COLLISION WARNING: MOVE AWAY FROM EDGE!",
                            color = Color(0xFFFEF2F2),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // --- 2.7 성간 크로노 타임 슬로우 비네트 및 경보 HUD 오버레이 ---
            if (gameState == GameState.PLAYING && chronoSlowTime > 0f) {
                val waveAlpha = rememberInfiniteTransition(label = "chrono_pulse").animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "chrono_alpha"
                ).value

                // 화면 테두리 시안(청록색) 하이테크 감속 비네트 오버레이
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 4.dp,
                            brush = SolidColor(Color(0xFF06B6D4).copy(alpha = waveAlpha * 0.7f)),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF0891B2).copy(alpha = waveAlpha * 0.16f)
                                ),
                                radius = 2200f
                            )
                        )
                )

                // 화면 중앙 실시간 타이머 바 오버레이 (설명 메시지 밀집도를 정합하기 위해 중앙 하부 배치)
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 150.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val formattedTime = String.format(Locale.getDefault(), "%.1f", chronoSlowTime)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(Color(0xE6083344), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .border(1.dp, Color(0xFF22D3EE), shape = RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "🌀",
                            fontSize = 12.sp
                        )
                        Text(
                            text = "크로노 필드 작동 중: ${formattedTime}초 남음",
                            color = Color(0xFFECFEFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // --- 2.7.5 클린 보이드 필드 비네트 및 황금빛 HUD 오버레이 ---
            if (gameState == GameState.PLAYING && cleanTime > 0f) {
                val waveAlpha = rememberInfiniteTransition(label = "clean_pulse").animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "clean_alpha"
                ).value

                // 화면 테두리 황금빛 임베디드 오버레이
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 4.dp,
                            brush = SolidColor(Color(0xFFFBBF24).copy(alpha = waveAlpha * 0.7f)),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF78350F).copy(alpha = waveAlpha * 0.12f)
                                ),
                                radius = 2200f
                            )
                        )
                )

                // 중앙 정렬 전술 타이머 비콘
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = if (chronoSlowTime > 0f) 210.dp else 150.dp), // 크로노 필드와 중첩될 경우 아래로 밀기
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val formattedTime = String.format(Locale.getDefault(), "%.1f", cleanTime)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(Color(0xE6451A03), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .border(1.2.dp, Color(0xFFFBBF24), shape = RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "✨",
                            fontSize = 12.sp
                        )
                        Text(
                            text = "클린 필드 작동 중: ${formattedTime}초 남음",
                            color = Color(0xFFFEF3C7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // --- 2.8 전술 아이템 획득 한글 안내 오버레이 (Floating Tactical Notification Card) ---
            itemMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 120.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xE60F172A),
                                        Color(0xCC1E293B)
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(1.5.dp, Color(0xFF10B981), shape = RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = msg,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // --- 3. 오퍼레이션 버튼 패널 (버튼: Tactical Strike Plasma Bomb - 극강의 SF HUD 구성) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 상호작용 지시 가이드라인 (Drag to Navigate - Sleek style)
                if (gameState == GameState.PLAYING) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)))
                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)))
                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "DRAG TO NAVIGATE",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // 화력 시스템 상태 한 줄 표시바 (HUD Indicator)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val pulseAlpha = rememberInfiniteTransition(label = "pulse_led").animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = EaseInOutSine),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "led_alpha"
                        ).value

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (bombCount > 0) Color(0xFFEF4444).copy(alpha = pulseAlpha)
                                    else Color(0xFF64748B)
                                )
                        )

                        Text(
                            text = if (bombCount > 0) "WEAPON READY" else "SYSTEM CHARGING",
                            color = if (bombCount > 0) Color(0xFFEF4444) else Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }

                    val nextChargeTimeText = if (bombCount > 0) {
                        "CHARGE MAX"
                    } else {
                        val remains = ((1f - bombGauge) * 15f).coerceAtLeast(0f)
                        String.format(Locale.getDefault(), "NEXT CHARGE: %02ds", remains.toInt())
                    }

                    Text(
                        text = nextChargeTimeText,
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }

                // High Visibility Bomb Button (Sleek Interface: Plasma Bomb 20dp width)
                Button(
                    onClick = { viewModel.triggerBomb() },
                    enabled = bombCount > 0 && gameState == GameState.PLAYING,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent, // 그라디언트는 커스텀 백그라운드 처리
                        disabledContainerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(0.dp), // 이너 패딩 제압
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .scale(if (bombCount > 0 && gameState == GameState.PLAYING) 1.0f else 0.98f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (bombCount > 0 && gameState == GameState.PLAYING) {
                                Brush.horizontalGradient(listOf(Color(0xFFDC2626), Color(0xFFEA580C))) // Red to Orange gradient
                            } else {
                                Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A))) // Slate disabled
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (bombCount > 0 && gameState == GameState.PLAYING) Color.White.copy(alpha = 0.4f) else Color(0x33FFFFFF),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .testTag("bomb_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Glass Counter Container (수량 카운트가 왼쪽에 배치됨)
                        Box(
                            modifier = Modifier
                                .size(width = 46.dp, height = 46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (bombCount > 0 && gameState == GameState.PLAYING) Color.White.copy(alpha = 0.15f)
                                    else Color.Black.copy(alpha = 0.3f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (bombCount > 0 && gameState == GameState.PLAYING) Color.White.copy(alpha = 0.3f) else Color(0x1AFFFFFF),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$bombCount",
                                color = if (bombCount > 0 && gameState == GameState.PLAYING) Color.White else Color(0xFF475569),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 폭탄 아이콘 및 폭탄 정보 텍스트 (우측에 배치됨)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "TACTICAL STRIKE",
                                    color = if (bombCount > 0 && gameState == GameState.PLAYING) Color(0xFFFFE4E6).copy(alpha = 0.7f) else Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "PLASMA BOMB",
                                    color = if (bombCount > 0 && gameState == GameState.PLAYING) Color.White else Color(0xFF475569),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp
                                )
                            }
                            BombIcon(
                                modifier = Modifier.size(52.dp),
                                isCharging = bombCount == 0 || gameState != GameState.PLAYING
                            )
                        }
                    }
                }
            }

            // --- 4. 게임 오버 / 조작 전 대기 가시성 레이어 오버레이 ---

            // (A) 최초 대기 상태 (READY)
            AnimatedVisibility(
                visible = gameState == GameState.READY,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF020617), // Deep Obsidian Black
                                    Color(0xFF0F172A), // Midnight Blue
                                    Color(0xFF02040A)  // Outer Space Black
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // 성간 배경 우주 격자 및 네뷸라 광원 그리개 (Aesthetic Starfield Visualization)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        // 은은한 인디고 성운 광선 효과
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0x1F6366F1), Color.Transparent),
                                center = Offset(w * 0.5f, h * 0.4f),
                                radius = w * 0.6f
                            )
                        )
                        // 은은한 시안 우주선 광선 효과
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0x1706B6D4), Color.Transparent),
                                center = Offset(w * 0.5f, h * 0.7f),
                                radius = w * 0.5f
                            )
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. [위치 변경] 세련되고 입체적인 타이틀 카드 Box가 상단으로 배치됨
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0x33020617), // 반투명 유리 Slate
                                            Color(0x1F0F172A)
                                        )
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .border(
                                    width = 1.5.dp,
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF06B6D4).copy(alpha = 0.6f),
                                            Color(0xFF6366F1).copy(alpha = 0.2f),
                                            Color(0xFF10B981).copy(alpha = 0.6f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(vertical = 24.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // 극강의 SF 텍스트 그라디언트 및 네온 스타일 제목 적용
                                Text(
                                    text = "SPACE SURVIVOR 3D",
                                    style = TextStyle(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF22D3EE), // Neon Cyan
                                                Color(0xFF818CF8), // Cyber Indigo
                                                Color(0xFF34D399)  // Matrix Emerald
                                            )
                                        ),
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 2.5.sp,
                                        textAlign = TextAlign.Center
                                    )
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "3D 가상 보이드 비행 물리 서바이벌\n다가오는 소행성과 적들의 포화를 격파하세요!",
                                    color = Color(0xFF94A3B8), // Sleek slate text
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // 2. [위치 변경] 기체 그림 배치 (HolographicRadarThumbnail이 타이틀 밑으로 배치됨)
                        HolographicRadarThumbnail(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .padding(bottom = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // 3. 비행 미션 개시 버튼
                        Button(
                            onClick = { viewModel.startGame() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF06B6D4), // Cyan theme button
                                contentColor = Color(0xFF020617)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .widthIn(min = 220.dp)
                                .height(56.dp)
                                .testTag("start_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "시작")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "비행 미션 개시",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 기타 제어 조작 로직
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showControlsDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF22D3EE)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "조작법",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("조작법", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { showScoreHistoryDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFA855F7) // Purple accent for special score board
                                ),
                                border = BorderStroke(1.5.dp, Color(0xFFA855F7).copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.2f).height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = "전적 관리",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("전적 관리", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { showAboutDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF22D3EE)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "게임 정보",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("정보", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 4. 홈화면 하단 중앙의 은은한 "GPT PARK" 라이센서 표시 오버레이
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 12.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "GPT PARK",
                                color = Color(0xFF06B6D4).copy(alpha = 0.35f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 4.sp
                            )
                            Text(
                                text = "SYSTEM ARCHITECT & VISUALS",
                                color = Color(0xFF475569).copy(alpha = 0.4f),
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }

            // (B) 미션 패배 게임 오버 (GAMEOVER)
            AnimatedVisibility(
                visible = gameState == GameState.GAMEOVER,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFA090514)), // Sleek Dark Red/Slate backdrop tint
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "경보",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier
                                .size(88.dp)
                                .scale(
                                    rememberInfiniteTransition(label = "pulse").animateFloat(
                                        initialValue = 0.95f,
                                        targetValue = 1.1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(800, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "pulsing"
                                    ).value
                                )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "MISSION FAILED",
                            color = Color(0xFFEF4444),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Thin, // Sleek lightweight styling
                            letterSpacing = 3.sp,
                            fontFamily = FontFamily.SansSerif,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "우주선 쉴드가 한계 임계치를 초과하여 파괴되었습니다.",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0x33EF4444) // Glassmorphic red glow
                            ),
                            border = BorderStroke(width = 1.dp, color = Color(0xFFEF4444).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "최종 성간 워프 거리",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = String.format(Locale.getDefault(), "%,.3f 광년", score),
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(40.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                        ) {
                            Button(
                                onClick = { viewModel.startGame() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF4444),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("restart_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "재시작")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "재시작",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Button(
                                onClick = { viewModel.resetToReady() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF334155),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("home_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "홈화면"
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "홈화면",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- 5. 이전 랭크 점수 보기 다이얼로그 (Dialog - Sleek Interface Dark Theme) ---
    if (showScoreHistoryDialog) {
        Dialog(onDismissRequest = { showScoreHistoryDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)), // Sleek Slate 900 background
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(width = 1.dp, color = Color(0x3338BDF8))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌌 최고 사투 전적 목록",
                            color = Color(0xFF38BDF8),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 0.5.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (allScores.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "기록된 전적이 아직 존재하지 않습니다.",
                                color = Color(0xFF64748B),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(allScores) { entity ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0x0F38BDF8), RoundedCornerShape(10.dp))
                                        .border(1.dp, Color(0x1F38BDF8), RoundedCornerShape(10.dp))
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = formatTimestamp(entity.timestamp),
                                            color = Color(0xFF64748B),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        text = String.format(Locale.getDefault(), "%,.3f 광년", entity.scoreValue),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.clearScores() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("기록 초기화", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Button(
                            onClick = { showScoreHistoryDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color(0xFF0F172A)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("닫기", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // --- 6. 조작법 안내 다이얼로그 (Dialog) ---
    if (showControlsDialog) {
        Dialog(onDismissRequest = { showControlsDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)), // Sleek Slate 900
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎮 우주선 조작법 안내",
                        color = Color(0xFF22D3EE),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 조작법 1
                        Row(verticalAlignment = Alignment.Top) {
                            Text("👉", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                            Column {
                                Text("단일 드래그 (조종)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("화면 아무 곳이나 손가락으로 드래그하면 전투기가 손가락 움직임에 최적화되어 상하좌우로 기동합니다.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                        }

                        // 조작법 2
                        Row(verticalAlignment = Alignment.Top) {
                            Text("⚡", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                            Column {
                                Text("더블 터치 유지 (초고속 가속)", color = Color(0xFFFF5252), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("화면에 두 손가락 이상을 동시에 대고 유지하면 하이퍼 드라이브 기동! 비행 속도가 급상승하며 추진 배출 불꽃이 맹렬한 붉은 색으로 분출됩니다. 화면에서 손을 떼면 원래 비행 속도로 돌아갑니다.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                        }

                        // 조작법 3
                        Row(verticalAlignment = Alignment.Top) {
                            Text("💥", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                            Column {
                                Text("플라즈마 폭탄 투하", color = Color(0xFFFFD740), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("하단의 PLASMA BOMB 버튼을 터치하여 동체 에너지 충적파를 발생시켜 전면에 출현한 모든 유성을 한순간에 소멸시킬 수 있습니다.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                        }

                        // 조작법 4
                        Row(verticalAlignment = Alignment.Top) {
                            Text("⚠️", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                            Column {
                                Text("가장자리 경보 및 피해", color = Color(0xFFEF4444), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("전투기가 화면 최내곽 가장자리에 직접 부딪히거나, 해당 경고 영역 부근에 1초 이상 머무를 경우 기체가 고농도 성간 정전기 마찰 충격으로 손상됩니다. 장애물 충돌과 동일하게 쉴드가 차감되므로 경보가 발생하면 즉각 안전 구역으로 탈출하세요!", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                        }

                        // 조작법 5
                        Row(verticalAlignment = Alignment.Top) {
                            Text("🔮", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                            Column {
                                Text("3D 전술 기능성 아이템 및 부스트 전술", color = Color(0xFF10B981), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("심우주 비행 중 일정 누적 시간(12초)마다 4가지 3D 고에너지 전술 아이템이 스폰되어 플레이어 방향으로 전진합니다.\n" +
                                        "• 🛡️ (녹색 보호막) : 손상된 쉴드를 1 슬롯 복구합니다 (최대 3).\n" +
                                        "• 🌀 (청록 크로노) : 주위 장애물과 적선 기동 속도를 5초간 0.5배속으로 즉시 감속시킵니다.\n" +
                                        "• ✨ (황금 클린) : 3초간 클린 보이드 필드를 활성화하여 전방의 모든 장애물을 정화/소멸시킵니다.\n" +
                                        "• ⚡ (보라 일렉트론) : 플라즈마 광역 대기 폭탄을 +1 충전합니다. (출현 빈도 조정됨)\n" +
                                        "※ 폭탄 게이지는 비행 속도나 부스팅 가속 여부와 무관하게 실시간 15초마다 정직하게 1개씩 자동으로 충전됩니다.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showControlsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4), contentColor = Color(0xFF020617)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("미션 숙지 확인", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // --- 7. 개발자 및 게임 정보 다이얼로그 (Dialog) ---
    if (showAboutDialog) {
        Dialog(onDismissRequest = { showAboutDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)), // Sleek Slate 900
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🌌 스페이스 서바이버 3D 정보",
                        color = Color(0xFF22D3EE),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "SCS Interceptor - Version 0.8",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 상세 정보 항목들
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 개발자 정보
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("개발자", color = Color(0xFF64748B), fontSize = 13.sp)
                            Text("GPT PARK", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        Divider(color = Color(0x1AFFFFFF))

                        // 유튜브 바로가기 정보
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("유튜브 공식 채널", color = Color(0xFF64748B), fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    try {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://www.youtube.com/@AIFACT-GPTPARK")
                                        )
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0x22EF4444),
                                    contentColor = Color(0xFFFF8A80)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🎬 유튜브 보러가기 ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "@AIFACT-GPTPARK",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFFFFC0BD)
                                    )
                                }
                            }
                        }

                        Divider(color = Color(0x1AFFFFFF))

                        // 공식 외부 사이트 정보
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("공식 사이트 안내", color = Color(0xFF64748B), fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    try {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://gptparkai.com/")
                                        )
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0x220284C7),
                                    contentColor = Color(0xFF38BDF8)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 12.dp)
                            ) {
                                Text("🌐 사이트 바로가기 (gptparkai.com)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = { showAboutDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4), contentColor = Color(0xFF020617)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("닫기", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// 간단 타임스탬프 포맷 도구
private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
    return sdf.format(date)
}

// 둥근 3D 구형 스타일의 폭탄 및 타오르는 스파크 불꽃 애니메이션 커스텀 아이콘
@Composable
fun BombIcon(modifier: Modifier = Modifier, isCharging: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "bomb_spark")
    val sparkScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "spark"
    )
    
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f + h * 0.1f // 폭탄 본체를 약간 아래로 배치
        val r = w * 0.32f
        
        // 1. 도화선 (Fuse) - S자 유려한 곡선
        val fusePath = Path().apply {
            moveTo(cx, cy - r)
            cubicTo(
                cx + r * 0.4f, cy - r * 1.3f,
                cx - r * 0.3f, cy - r * 1.7f,
                cx + r * 0.1f, cy - r * 2.1f
            )
        }
        drawPath(
            path = fusePath,
            color = Color(0xFFA1A1AA),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 4f,
                cap = StrokeCap.Round
            )
        )
        
        // 2. 금속 폭탄 뚜껑 (Fuse Holder)
        drawRect(
            color = Color(0xFF71717A),
            topLeft = Offset(cx - r * 0.2f, cy - r - 6f),
            size = androidx.compose.ui.geometry.Size(r * 0.4f, 8f)
        )
        
        // 3. 폭탄 본체 무광 검정 3D 둥근 입체 구 (Sphere Graphite Gradient)
        val bombBrush = Brush.radialGradient(
            colors = listOf(Color(0xFF4B5563), Color(0xFF1F2937), Color(0xFF030712)),
            center = Offset(cx - r * 0.3f, cy - r * 0.3f),
            radius = r
        )
        drawCircle(
            brush = bombBrush,
            radius = r,
            center = Offset(cx, cy)
        )
        
        // 4. 불타는 도화선 불꽃 (Glowing Spark)
        val sparkX = cx + r * 0.1f
        val sparkY = cy - r * 2.1f
        
        if (!isCharging) {
            // 외부 은은한 불꽃 반사
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFAED), Color(0xFFFBBF24), Color(0xFFEF4444), Color.Transparent),
                    center = Offset(sparkX, sparkY),
                    radius = r * 0.5f * sparkScale
                ),
                radius = r * 0.5f * sparkScale,
                center = Offset(sparkX, sparkY)
            )
            // 지글거리는 데코 광선들
            for (i in 0 until 4) {
                val angle = (i * 90f) + (sparkScale * 45f)
                val rad = Math.toRadians(angle.toDouble())
                val lineLen = r * 0.3f * sparkScale
                val lineStart = r * 0.1f
                val startX = (sparkX + lineStart * cos(rad)).toFloat()
                val startY = (sparkY + lineStart * sin(rad)).toFloat()
                val endX = (sparkX + (lineStart + lineLen) * cos(rad)).toFloat()
                val endY = (sparkY + (lineStart + lineLen) * sin(rad)).toFloat()
                drawLine(
                    color = Color(0xFFFBBF24),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

// 강력하고 세련된 홀로그래픽 택티컬 레이다 및 회전하는 3D 스페이스 서바이버 3D 입체 도면 시각화 썸네일
@Composable
fun HolographicRadarThumbnail(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "hologram")
    
    // 가상 비행 각도 천천히 회전 (Yaw)
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hologram_rot"
    )
    
    // 신호 오프닝 맥박 효과
    val ringPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar_pulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x22020617)) // 깊고 투명한 네온 다크 슬레이트
            .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
    ) {
        // 무한 수직 스캔 라인 레이저
        val scanPercent by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(3800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "scanner"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            
            // 1. 전술 메쉬 좌표 그리드 선
            val gridStep = 20.dp.toPx()
            val gridColor = Color(0xFF0891B2).copy(alpha = 0.08f)
            
            // 수직
            var x = 0f
            while (x < w) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), 1f)
                x += gridStep
            }
            // 수평
            var y = 0f
            while (y < h) {
                drawLine(gridColor, Offset(0f, y), Offset(w, y), 1f)
                y += gridStep
            }
            
            // 2. 동심원 탐지 레이더 링
            val rMax = Math.min(cx, cy) * 0.9f
            drawCircle(
                color = Color(0xFF06B6D4).copy(alpha = 0.04f * ringPulse),
                radius = rMax * 0.45f,
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
            )
            drawCircle(
                color = Color(0xFF06B6D4).copy(alpha = 0.07f),
                radius = rMax * 0.75f,
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
            )
            drawCircle(
                color = Color(0xFF22D3EE).copy(alpha = 0.14f),
                radius = rMax,
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 16f), 0f)
                )
            )
            
            // 십자 추적 가이드선
            drawLine(
                color = Color(0xFF0891B2).copy(alpha = 0.1f),
                start = Offset(cx - rMax, cy),
                end = Offset(cx + rMax, cy),
                strokeWidth = 1f
            )
            drawLine(
                color = Color(0xFF0891B2).copy(alpha = 0.1f),
                start = Offset(cx, cy - rMax),
                end = Offset(cx, cy + rMax),
                strokeWidth = 1f
            )
            
            // 3. 3D 구도 회전하는 입체 전술 설계도 (Hologram Wireframe Spaceship)
            val yawRad = Math.toRadians(rotationAngle.toDouble()).toFloat()
            val pitchRad = Math.toRadians(-24.0).toFloat() // 살짝 내려다보는 고정 3D 구도
            
            // 3D 우주선 설계도 점 세팅
            val model3D = listOf(
                Point3D(0f, -46f, 0f),      // 0: Cockpit Nose
                Point3D(-36f, 15f, 6f),     // 1: Left Wing
                Point3D(36f, 15f, 6f),      // 2: Right Wing
                Point3D(0f, 26f, -10f),     // 3: Engines Center
                Point3D(0f, -6f, -18f)      // 4: Bubble Canopy
            )
            
            // 3D 회전행렬 연산 후 투영좌표 도출
            val pts = model3D.map { pt ->
                // Z축 회전 (Yaw)
                val x1 = pt.x * cos(yawRad) - pt.y * sin(yawRad)
                val y1 = pt.x * sin(yawRad) + pt.y * cos(yawRad)
                val z1 = pt.z
                
                // X축 회전 (Pitch)
                val x2 = x1
                val y2 = y1 * cos(pitchRad) - z1 * sin(pitchRad)
                
                // 원근 정사투영 스케일
                val s = 1.5f
                Offset(cx + x2 * s, cy + y2 * s)
            }
            
            // 와이어 프레임 벡터 라인 연결 (네온 시안 컬러 정밀 드로잉)
            val cyanCyan = Color(0xFF22D3EE)
            val strokeThick = 2.2f
            val strokeThin = 1.2f
            
            // 날개 & 기수 결속
            drawLine(cyanCyan.copy(alpha = 0.85f), pts[0], pts[1], strokeWidth = strokeThick)
            drawLine(cyanCyan.copy(alpha = 0.85f), pts[0], pts[2], strokeWidth = strokeThick)
            // 동체 하단부 결속
            drawLine(cyanCyan.copy(alpha = 0.65f), pts[1], pts[3], strokeWidth = strokeThick)
            drawLine(cyanCyan.copy(alpha = 0.65f), pts[2], pts[3], strokeWidth = strokeThick)
            // 캐노피 입체 결속
            drawLine(cyanCyan.copy(alpha = 0.75f), pts[0], pts[4], strokeWidth = strokeThin)
            drawLine(cyanCyan.copy(alpha = 0.75f), pts[3], pts[4], strokeWidth = strokeThin)
            drawLine(cyanCyan.copy(alpha = 0.4f), pts[1], pts[4], strokeWidth = strokeThin)
            drawLine(cyanCyan.copy(alpha = 0.4f), pts[2], pts[4], strokeWidth = strokeThin)
            
            // 꼭짓점 네온 플라즈마 노드 광원 연출
            for (pt in pts) {
                drawCircle(
                    color = Color.White,
                    radius = 3f,
                    center = pt
                )
                drawCircle(
                    color = Color(0xFF06B6D4).copy(alpha = 0.45f * ringPulse),
                    radius = 8.5f * ringPulse,
                    center = pt
                )
            }
            
            // 4. 수직 하이테크 레이저 스캔 모션
            val scannerY = scanPercent * h
            drawLine(
                color = Color(0xFF22D3EE).copy(alpha = 0.4f),
                start = Offset(0f, scannerY),
                end = Offset(w, scannerY),
                strokeWidth = 2.2f
            )
            // 잔상 효과 (아래로 퍼지는 반투명 네온 광학 그라디언트)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF22D3EE).copy(alpha = 0.1f), Color.Transparent),
                    startY = scannerY - 35f,
                    endY = scannerY
                ),
                topLeft = Offset(0f, Math.max(0f, scannerY - 35f)),
                size = androidx.compose.ui.geometry.Size(w, Math.min(35f, scannerY))
            )
            
            // 코너 하이테크 요철 보조 브래킷 장식 (Sci-fi Corner HUD elements)
            val brkSize = 12.dp.toPx()
            val strokeB = 2.dp.toPx()
            val bColor = Color(0xFF0369A1).copy(alpha = 0.8f)
            
            // 좌상
            drawLine(bColor, Offset(4.dp.toPx(), 4.dp.toPx()), Offset(4.dp.toPx() + brkSize, 4.dp.toPx()), strokeWidth = strokeB)
            drawLine(bColor, Offset(4.dp.toPx(), 4.dp.toPx()), Offset(4.dp.toPx(), 4.dp.toPx() + brkSize), strokeWidth = strokeB)
            // 우상
            drawLine(bColor, Offset(w - 4.dp.toPx(), 4.dp.toPx()), Offset(w - 4.dp.toPx() - brkSize, 4.dp.toPx()), strokeWidth = strokeB)
            drawLine(bColor, Offset(w - 4.dp.toPx(), 4.dp.toPx()), Offset(w - 4.dp.toPx(), 4.dp.toPx() + brkSize), strokeWidth = strokeB)
            // 좌하
            drawLine(bColor, Offset(4.dp.toPx(), h - 4.dp.toPx()), Offset(4.dp.toPx() + brkSize, h - 4.dp.toPx()), strokeWidth = strokeB)
            drawLine(bColor, Offset(4.dp.toPx(), h - 4.dp.toPx()), Offset(4.dp.toPx(), h - 4.dp.toPx() - brkSize), strokeWidth = strokeB)
            // 우하
            drawLine(bColor, Offset(w - 4.dp.toPx(), h - 4.dp.toPx()), Offset(w - 4.dp.toPx() - brkSize, h - 4.dp.toPx()), strokeWidth = strokeB)
            drawLine(bColor, Offset(w - 4.dp.toPx(), h - 4.dp.toPx()), Offset(w - 4.dp.toPx(), h - 4.dp.toPx() - brkSize), strokeWidth = strokeB)
        }
        
        // 전술 터미널 좌측 오버레이 진단글
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Text(
                text = "SYS_STATUS: ACTIVE",
                color = Color(0xFF22D3EE),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "STARSHIP: INTERCEPTOR.V5",
                color = Color(0xFF0284C7),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "ANTIGRAV_THRUST: OK",
                color = Color(0xFF94A3B8),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        
        // 전술 터미널 우측 오버레이 좌표 상태 글
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "SHIELD: 100% GAUGE",
                color = Color(0xFF10B981), // 녹색 정상 레이블
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "BOMB: PLASMA CHARGING",
                color = Color(0xFFA1A1AA),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
