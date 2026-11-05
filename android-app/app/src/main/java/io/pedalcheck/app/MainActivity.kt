package io.pedalcheck.app

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*

/** Display first; all simulated inputs live on an explicit, separate tools page. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val store by lazy { SharedDemoStore(this) }
    private val preferences by lazy { getSharedPreferences("display", MODE_PRIVATE) }
    private val demo by lazy { DemoSignalSource { store.read() } }
    private val unavailable = UnavailablePleosSignalSource()
    private lateinit var source: VehicleSignalSource
    private var engine = PedalEngine()
    private var active = false
    private val warnings get() = preferences.getBoolean("warnings", true)
    private var page = "home"
    private var output = PedalOutput(PedalState.UNKNOWN)
    private var painted: PedalOutput? = null
    private lateinit var body: LinearLayout
    private var pageScroll: ScrollView? = null
    private val scrollPositions = mutableMapOf<String, Int>()
    private var warningLabel: TextView? = null
    private var heroEyebrow: TextView? = null
    private var headline: TextView? = null
    private var explanation: TextView? = null
    private var connection: TextView? = null
    private var statusTag: TextView? = null
    private var hero: LinearLayout? = null
    private var brakeCard: PedalCard? = null
    private var accelCard: PedalCard? = null
    private val night get() = preferences.getBoolean("night", false)
    private val sizeIndex get() = preferences.getInt("size", 0).coerceIn(0, 2)
    private val textScale get() = listOf(1f, 1.2f, 1.4f)[sizeIndex]
    private val bg get() = c(if (night) "#12161C" else "#F5F5F2")
    private val surface get() = c(if (night) "#1E252E" else "#FFFFFF")
    private val ink get() = c(if (night) "#F4F6FA" else "#202B38")
    private val muted get() = c(if (night) "#B4BECC" else "#647080")
    private val line get() = c(if (night) "#38424F" else "#E1E5E9")
    private val accent get() = c(if (night) "#94BAFF" else "#245AC3")
    private val watchdog = object : Runnable {
        override fun run() { if (active) { render(engine.tick(SystemClock.elapsedRealtime())); handler.postDelayed(this, 50) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = savedInstanceState?.getString("page") ?: "home"
        source = if (store.read().demoSource) demo else unavailable
        scrollPositions[page] = savedInstanceState?.getInt("scroll") ?: 0
        buildPage()
    }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putString("page", page); outState.putInt("scroll", pageScroll?.scrollY ?: 0) }
    override fun onStart() {
        super.onStart(); active = true; WarningAudio.attach(this, this); window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        engine = PedalEngine(EngineOptions(experimentalWarnings = warnings)); startSource(); handler.post(watchdog)
    }
    override fun onStop() {
        active = false; WarningAudio.detach(this); source.stop(); handler.removeCallbacks(watchdog); render(engine.disconnected())
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); super.onStop()
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }
    override fun onPostResume() {
        super.onPostResume()
        if (!BuildConfig.DEBUG) return
        if (intent.getBooleanExtra("pedal_test_auto_cycle", false)) store.cycle(true)
        if (intent.getBooleanExtra("pedal_test_start_overlay", false)) startMockOverlay()
        intent.removeExtra("pedal_test_auto_cycle"); intent.removeExtra("pedal_test_start_overlay")
    }
    @Deprecated("Compatible with API 28")
    override fun onBackPressed() { if (page != "home") navigate("home") else super.onBackPressed() }
    private fun navigate(next: String) { scrollPositions[page] = pageScroll?.scrollY ?: 0; pageScroll = null; page = next; scrollPositions[next] = 0; buildPage() }
    private fun startSource() {
        render(PedalOutput(PedalState.UNKNOWN, reason = "신호 대기"))
        source.start({ if (active) render(engine.accept(it, SystemClock.elapsedRealtime())) }, { if (active) render(engine.disconnected(it)) })
    }

    private fun buildPage() {
        val restoreScroll = pageScroll?.scrollY ?: scrollPositions[page] ?: 0
        warningLabel = null; heroEyebrow = null
        headline = null; explanation = null; connection = null; statusTag = null; hero = null; brakeCard = null; accelCard = null; painted = null
        window.statusBarColor = bg; window.navigationBarColor = bg
        window.decorView.systemUiVisibility = if (night) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val frame = android.widget.FrameLayout(this).apply { setBackgroundColor(bg) }
        val scroll = PedalScrollView(this).apply { isFillViewport = true; clipToPadding = false; overScrollMode = View.OVER_SCROLL_NEVER }
        pageScroll = scroll
        body = column().apply { setPadding(dp(32), dp(28), dp(32), dp(28)); isFocusableInTouchMode = true; descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS }
        // Cap line length on wide displays; use the entire available split pane on Pleos.
        val maxWidth = minOf(resources.configuration.screenWidthDp, 1120)
        scroll.addView(body, android.widget.FrameLayout.LayoutParams(-1, -2))
        frame.addView(scroll, android.widget.FrameLayout.LayoutParams(dp(maxWidth), -1, Gravity.CENTER_HORIZONTAL))
        frame.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom); insets
        }
        setContentView(frame)
        when (page) { "settings" -> settingsPage(); "tools" -> toolsPage(); else -> homePage() }
        body.requestFocus()
        render(output)
        scroll.post { scroll.scrollTo(0, restoreScroll) }
    }

    private fun homePage() {
        val compactHeader = resources.configuration.screenWidthDp < 700
        val header = if (compactHeader) column() else row()
        val brand = column()
        brand.addView(label("페달체크", 30f, bold = true))
        header.addView(brand, if (compactHeader) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f))
        val actions = if (compactHeader) row().also { gap(header, 12); header.addView(it) } else header
        actions.addView(button("시연 도구 열기", false) { navigate("tools") }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(10) })
        actions.addView(button("화면 설정", false) { navigate("settings") })
        body.addView(header); gap(body, 28)

        val top = row()
        top.addView(label("현재 페달 상태", 22f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        statusTag = label("시연 모드", 17f, muted).apply { setPadding(dp(14), dp(8), dp(14), dp(8)); background = shape(surface, 30) }
        top.addView(statusTag); body.addView(top); gap(body, 16)
        hero = column().apply { setPadding(dp(30), dp(28), dp(30), dp(30)); background = shape(surface) }
        heroEyebrow = label("지금 밟고 있는 페달", 20f, muted)
        hero!!.addView(heroEyebrow)
        warningLabel = label("⚠  경고", 28f, Color.WHITE, true).apply { visibility = View.GONE }
        hero!!.addView(warningLabel)
        headline = label("확인 중", if (resources.configuration.screenWidthDp < 600) 48f else 64f, bold = true).apply { setPadding(0, dp(8), 0, dp(4)); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        explanation = label("페달 신호를 확인하고 있어요", 23f, muted)
        hero!!.addView(headline); hero!!.addView(explanation)
        body.addView(hero); gap(body, 20)

        val pedals = row()
        brakeCard = PedalCard(true); accelCard = PedalCard(false)
        pedals.addView(brakeCard!!.view, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(10) })
        pedals.addView(accelCard!!.view, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        body.addView(pedals); gap(body, 22)
        body.addView(label("왼쪽 브레이크 · 오른쪽 액셀", 19f, muted).apply { gravity = Gravity.CENTER })
        gap(body, 28)
        val info = column().apply { setPadding(dp(22), dp(18), dp(22), dp(18)); background = shape(surface, 18) }
        connection = label("모의 신호 수신 중", 20f, bold = true)
        info.addView(connection)
        info.addView(label("실제 차량에는 연결되지 않았어요", 18f, muted))
        body.addView(info)
        gap(body, 22)
        body.addView(label("운전 중에는 조작하지 않고, 필요한 순간에만 확인하세요.", 19f, muted))
        gap(body, 22)
        val footer = row()
        footer.addView(label("PEDALCHECK", 15f, muted, true).apply { letterSpacing = 0.12f }, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(footer)
    }

    private inner class PedalCard(val brake: Boolean) {
        val view = column().apply { gravity = Gravity.CENTER; setPadding(dp(12), dp(22), dp(12), dp(22)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES }
        val name = label(if (brake) "브레이크" else "액셀", 28f, bold = true)
        val glyph = PedalGlyph(this@MainActivity, brake)
        val state = label("밟지 않음", 20f)
        init {
            view.addView(name)
            view.addView(glyph, LinearLayout.LayoutParams(-1, dp(132)).apply { topMargin = dp(8); bottomMargin = dp(8) })
            view.addView(state)
            name.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            state.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        fun update(pressed: Boolean, unknown: Boolean) {
            val tint = if (brake) PedalDesign.orange else PedalDesign.blue
            val foreground = if (pressed) Color.WHITE else muted
            view.background = shape(if (pressed) tint else surface, 24, if (pressed) tint else line)
            name.setTextColor(if (pressed) Color.WHITE else ink); glyph.ink = foreground; state.setTextColor(foreground)
            state.text = if (unknown) "확인 불가" else if (pressed) "●  밟고 있어요" else "밟지 않음"
            view.contentDescription = "${name.text}, ${state.text}"
        }
    }

    private fun pageHeader(title: String, description: String) {
        body.addView(button("‹  페달 화면으로", false) { navigate("home") }); gap(body, 24)
        body.addView(label(title, 36f, bold = true)); gap(body, 8); body.addView(label(description, 21f, muted)); gap(body, 28)
    }
    private fun settingsPage() {
        pageHeader("보기 편한 화면", "정차 중에 나에게 맞게 조절하세요.")
        val fonts = section("글씨 크기", "선택한 크기는 다음에도 유지돼요.")
        val choices = row()
        listOf("기본", "크게", "아주 크게").forEachIndexed { index, name ->
            choices.addView(button((if (sizeIndex == index) "✓ " else "") + name, sizeIndex == index) {
                preferences.edit().putInt("size", index).apply(); buildPage()
            }.apply { isSelected = sizeIndex == index; contentDescription = "글씨 크기 $name${if (isSelected) ", 선택됨" else ""}" }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        }
        fonts.addView(choices); gap(fonts, 22)
        fonts.addView(label("브레이크를 밟고 있어요", 28f, bold = true)); fonts.addView(label("이 크기로 페달 상태를 알려드려요.", 20f, muted))
        val display = section("화면 밝기", "어두운 곳에서는 눈부심을 줄여보세요.")
        display.addView(toggle("어두운 화면", night) { preferences.edit().putBoolean("night", it).apply(); buildPage() })
        val voice = section("경고 음성", "경고가 발생할 때만 한국어 음성으로 알려드려요.")
        voice.addView(toggle("경고 음성 사용", preferences.getBoolean("voice", true)) { preferences.edit().putBoolean("voice", it).apply(); WarningAudio.refresh() })
        gap(voice, 12); voice.addView(label("목소리", 21f, bold = true)); gap(voice, 10)
        val voiceChoices = row(); val selectedVoice = WarningVoice.from(preferences.getString(WarningVoice.PREFERENCE, null))
        WarningVoice.entries.forEach { option ->
            val selected = option == selectedVoice
            voiceChoices.addView(button((if (selected) "✓ " else "") + option.label, selected) {
                preferences.edit().putString(WarningVoice.PREFERENCE, option.name).apply(); WarningAudio.voiceChanged(); buildPage()
            }.apply { isSelected = selected; contentDescription = "경고 목소리 ${option.label}${if (selected) ", 선택됨" else ""}" }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        }
        voice.addView(voiceChoices); gap(voice, 18)
        voice.addView(label("동시 입력과 깊은 액셀 지속 시 안내합니다. 경고가 이어지면 8초 간격으로 반복하고, 해제되면 즉시 멈춰요. 인터넷 연결 없이 재생됩니다.", 20f, muted))
        val about = section("페달체크 안내", "페달 입력을 눈으로 확인하는 보조 화면입니다.")
        about.addView(label("페달 오조작을 판정하거나 차량을 제어하지 않아요. 현재는 실제 차량 신호 대신 모의 신호로 동작합니다.", 19f, muted))
        gap(about, 12); about.addView(label("글꼴  Pretendard · SIL Open Font License", 16f, muted))
    }
    private fun toolsPage() {
        pageHeader("시연 도구", "모의 입력으로 화면을 확인하세요. 정차 시연 전용입니다.")
        val presets = section("페달 상태 바꾸기", "선택하면 자동 순환이 멈추고 해당 상태를 유지해요.")
        listOf(listOf("입력 없음", "액셀"), listOf("브레이크", "동시 입력"), listOf("신호 끊김", "깊은 액셀")).forEachIndexed { rowIndex, names ->
            val r = row()
            names.forEachIndexed { columnIndex, name -> r.addView(button(name, false) {
                useDemo(); store.cycle(false)
                when (rowIndex * 2 + columnIndex) {
                    0 -> store.manual(0.0, false, 0.0, true)
                    1 -> store.manual(35.0, false, 0.0, true)
                    2 -> store.manual(0.0, true, 0.0, true)
                    3 -> store.manual(35.0, true, 0.0, true)
                    4 -> { store.manual(connected = false); render(engine.disconnected()) }
                    5 -> store.manual(100.0, false, 0.0, true)
                }
                navigate("home")
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8); bottomMargin = dp(8) }) }
            presets.addView(r)
        }
        val input = section("직접 조절", "자동 순환 중 직접 조절하면 수동 입력으로 바뀝니다.")
        val cycle = toggle("20초 자동 순환", store.isCycling()) { useDemo(); store.cycle(it) }; input.addView(cycle)
        fun manual() { useDemo(); if (cycle.isChecked) cycle.isChecked = false; store.cycle(false) }
        val values = store.read()
        slider(input, "액셀", values.accelerator.toInt(), 100, "%") { manual(); store.manual(accelerator = it.toDouble(), connected = true) }
        input.addView(toggle("브레이크 밟기", values.brake) { manual(); store.manual(brake = it, connected = true) })
        slider(input, "모의 속도", values.speed.toInt(), 160, " km/h") { manual(); store.manual(speed = it.toDouble(), connected = true) }
        val experiment = section("실험 기능", "실제 운전자의 의도를 판단하지 않습니다.")
        experiment.addView(toggle("경고 표시", warnings) {
            preferences.edit().putBoolean("warnings", it).apply(); engine = PedalEngine(EngineOptions(experimentalWarnings = it)); render(PedalOutput(PedalState.UNKNOWN, reason = "설정 변경"))
        })
        if (BuildConfig.DEBUG) {
            val overlay = section("다른 앱 위 작은 표시", "모의 페달 상태를 다른 화면에서도 확인합니다.")
            overlay.addView(button("작은 표시 시작", true) { startMockOverlay() }); gap(overlay, 10)
            overlay.addView(button("작은 표시 종료", false) { stopService(Intent(this, PedalOverlayService::class.java)); Toast.makeText(this, "작은 표시를 종료했어요", Toast.LENGTH_SHORT).show() }); gap(overlay, 10)
            overlay.addView(button("표시 권한 설정", false) {
                val settings = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                try { startActivity(settings) } catch (_: RuntimeException) { Toast.makeText(this, R.string.overlay_settings_unavailable, Toast.LENGTH_LONG).show() }
            })
        }
        val signals = section("신호 연결 확인", if (source === demo) "현재 모의 신호를 사용하고 있어요." else "실차 신호 연동은 준비 중입니다.")
        signals.addView(button(if (source === demo) "Pleos 연결 상태 확인" else "모의 신호로 돌아가기", false) {
            if (source === demo) {
                source.stop(); source = unavailable; store.setDemoSource(false); stopService(Intent(this, PedalOverlayService::class.java)); engine = PedalEngine(); startSource()
            } else useDemo()
            navigate("home")
        })
    }
    private fun useDemo() {
        if (source === demo) return
        source.stop(); source = demo; store.setDemoSource(true); engine = PedalEngine(EngineOptions(experimentalWarnings = warnings)); startSource()
    }
    private fun render(next: PedalOutput) {
        output = next
        if (active) WarningAudio.update(this, next.attention)
        if (page != "home" || painted == next) return
        painted = next
        val state = next.state
        val unknown = state == PedalState.UNKNOWN
        val attention = next.attention != Attention.NONE
        warningLabel?.visibility = if (attention) View.VISIBLE else View.GONE
        heroEyebrow?.visibility = if (attention) View.GONE else View.VISIBLE
        headline?.text = if (attention) when (next.attention) {
            Attention.SIMULTANEOUS -> "두 페달 동시 입력"
            else -> "깊은 액셀 지속"
        } else when (state) {
            PedalState.ACCELERATOR -> "액셀"
            PedalState.BRAKE -> "브레이크"
            PedalState.BOTH -> "두 페달 함께"
            PedalState.NEUTRAL -> "밟은 페달 없음"
            PedalState.UNKNOWN -> "페달 확인 불가"
        }
        val stateColor = when {
            attention -> if (night) c("#FF9EA8") else PedalDesign.red
            state == PedalState.ACCELERATOR -> accent
            state == PedalState.BRAKE -> if (night) c("#FFBB85") else PedalDesign.orange
            state == PedalState.BOTH -> if (night) c("#D0B0FF") else PedalDesign.purple
            else -> ink
        }
        headline?.setTextColor(if (attention) Color.WHITE else stateColor)
        explanation?.setTextColor(if (attention) Color.WHITE else muted)
        explanation?.text = if (attention) next.attention.message else when (state) {
            PedalState.ACCELERATOR -> "가속 페달을 밟고 있어요"
            PedalState.BRAKE -> "브레이크를 밟고 있어요"
            PedalState.BOTH -> "브레이크와 액셀을 함께 밟고 있어요"
            PedalState.NEUTRAL -> "두 페달 모두 밟고 있지 않아요"
            PedalState.UNKNOWN -> if (next.reason == "입력 안정화 확인 중" || next.reason == "신호 대기") "페달 신호를 확인하고 있어요" else "신호가 없어 현재 페달을 알 수 없어요"
        }
        hero?.background = shape(if (attention) PedalDesign.red else surface, 24, if (attention) PedalDesign.red else line)
        statusTag?.text = if (source === demo) "시연 모드" else "차량 미연결"
        connection?.text = if (source !== demo) "차량 신호 연결이 필요해요" else if (unknown) "모의 신호 확인 중" else "모의 신호 수신 중"
        brakeCard?.update(state == PedalState.BRAKE || state == PedalState.BOTH, unknown)
        accelCard?.update(state == PedalState.ACCELERATOR || state == PedalState.BOTH, unknown)
    }
    private fun startMockOverlay() {
        if (!BuildConfig.DEBUG) return
        if (source !== demo || !store.read().demoSource) { Toast.makeText(this, R.string.overlay_demo_only, Toast.LENGTH_LONG).show(); return }
        if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, R.string.overlay_permission_missing, Toast.LENGTH_LONG).show(); return }
        try { startForegroundService(Intent(this, PedalOverlayService::class.java)); Toast.makeText(this, "작은 모의 표시를 시작했어요", Toast.LENGTH_SHORT).show() }
        catch (_: RuntimeException) { Toast.makeText(this, R.string.overlay_start_failed, Toast.LENGTH_LONG).show() }
    }
    private fun section(title: String, subtitle: String): LinearLayout {
        val card = column().apply { setPadding(dp(24), dp(24), dp(24), dp(24)); background = shape(surface) }
        card.addView(label(title, 24f, bold = true)); gap(card, 6); card.addView(label(subtitle, 19f, muted)); gap(card, 20)
        body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) }); return card
    }
    private fun slider(parent: LinearLayout, title: String, value: Int, maxValue: Int, unit: String, change: (Int) -> Unit) {
        val caption = label(getString(R.string.demo_slider_value, title, value, unit), 21f); parent.addView(caption)
        parent.addView(SeekBar(this).apply {
            max = maxValue; progress = value; minimumHeight = dp(64); progressTintList = ColorStateList.valueOf(accent); thumbTintList = ColorStateList.valueOf(accent); contentDescription = "$title 모의 입력"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) { caption.text = getString(R.string.demo_slider_value, title, progress, unit); change(progress) } }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }, LinearLayout.LayoutParams(-1, -2))
    }
    private fun toggle(title: String, value: Boolean, change: (Boolean) -> Unit) = Switch(this).apply {
        text = title; textSize = 21f * textScale; typeface = PedalDesign.font(this@MainActivity); setTextColor(ink)
        minHeight = dp(72); setPadding(0, dp(12), 0, dp(12)); switchPadding = dp(18); isChecked = value
        thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(accent, muted))
        setOnCheckedChangeListener { _, checked -> change(checked) }
    }
    private fun button(title: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = title; textSize = 20f * textScale; typeface = PedalDesign.font(this@MainActivity, true); isAllCaps = false
        setTextColor(if (primary) Color.WHITE else ink); minHeight = dp(60); minimumHeight = dp(60)
        setPadding(dp(20), dp(14), dp(20), dp(14)); stateListAnimator = null
        background = RippleDrawable(ColorStateList.valueOf(c("#22777777")), shape(if (primary) PedalDesign.blue else surface, 16, if (primary) null else line), null)
        setOnClickListener { action() }
    }
    private fun label(value: String, size: Float, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size * textScale; setTextColor(color); typeface = PedalDesign.font(this@MainActivity, bold)
        includeFontPadding = false; setLineSpacing(dp(4).toFloat(), 1.06f)
    }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun gap(parent: LinearLayout, value: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(value))) }
    private fun shape(color: Int, radius: Int = 24, stroke: Int? = null) = PedalDesign.rounded(this, color, radius, stroke)
    private fun c(hex: String) = PedalDesign.color(hex)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
