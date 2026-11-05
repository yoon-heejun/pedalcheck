package io.pedalcheck.app

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.View

/** Shared visual language. Pedal geometry stays brake-left / accelerator-right. */
object PedalDesign {
    fun color(hex: String) = Color.parseColor(hex)
    val blue = color("#245AC3")
    val orange = color("#A94D12")
    val purple = color("#7547AD")
    val red = color("#BC3545")
    fun font(context: Context, bold: Boolean = false): Typeface =
        Typeface.create(context.resources.getFont(R.font.pretendard), if (bold) 650 else 400, false)
    fun rounded(context: Context, color: Int, radius: Int = 24, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius * context.resources.displayMetrics.density
        stroke?.let { setStroke((context.resources.displayMetrics.density).toInt().coerceAtLeast(1), it) }
    }
}

class PedalGlyph @JvmOverloads constructor(context: Context, private val brake: Boolean = false) : View(context) {
    var ink: Int = PedalDesign.blue
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = if (brake) RectF(18f, 44f, 142f, 120f) else RectF(49f, 14f, 111f, 145f)
    private val ribs = floatArrayOf(44f, 68f, 92f, 116f)
    private val treads = floatArrayOf(40f, 64f, 88f, 112f)
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / 160f, height / 160f)
        canvas.save(); canvas.translate((width - 160 * scale) / 2, (height - 160 * scale) / 2); canvas.scale(scale, scale)
        paint.color = ink; paint.style = Paint.Style.STROKE; paint.strokeWidth = 7f; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawRoundRect(box, 15f, 15f, paint)
        paint.strokeWidth = 5f
        if (brake) for (x in ribs) canvas.drawLine(x, 65f, x, 99f, paint)
        else for (y in treads) canvas.drawLine(68f, y, 92f, y, paint)
        canvas.restore()
    }
}
