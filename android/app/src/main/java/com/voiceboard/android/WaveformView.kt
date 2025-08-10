package com.voiceboard.android

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.sin
import kotlin.random.Random

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3") // Blue color
        style = Paint.Style.FILL
    }
    
    private val barCount = 20
    private val barWidthDp = 4f
    private val barSpacingDp = 4f
    private val minBarHeightDp = 4f
    private val maxBarHeightDp = 40f
    
    private val barWidth = dpToPx(barWidthDp)
    private val barSpacing = dpToPx(barSpacingDp)
    private val minBarHeight = dpToPx(minBarHeightDp)
    private val maxBarHeight = dpToPx(maxBarHeightDp)
    
    private val barHeights = FloatArray(barCount) { minBarHeight }
    private val targetHeights = FloatArray(barCount) { minBarHeight }
    
    private var animator: ValueAnimator? = null
    private var isAnimating = false
    private var animationPhase = 0f
    
    private val cornerRadius = dpToPx(2f)
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (barCount * barWidth + (barCount - 1) * barSpacing).toInt()
        val desiredHeight = maxBarHeight.toInt()
        
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        
        setMeasuredDimension(width, height)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (!isAnimating) return
        
        val totalBarsWidth = barCount * barWidth + (barCount - 1) * barSpacing
        val startX = (width - totalBarsWidth) / 2f
        val centerY = height / 2f
        
        for (i in 0 until barCount) {
            val x = startX + i * (barWidth + barSpacing)
            val barHeight = barHeights[i]
            val top = centerY - barHeight / 2f
            val bottom = centerY + barHeight / 2f
            
            // Draw rounded rectangle
            val rect = RectF(x, top, x + barWidth, bottom)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        }
    }
    
    fun startAnimation() {
        if (isAnimating) return
        
        isAnimating = true
        
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Long.MAX_VALUE // Continuous animation
            interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            
            addUpdateListener { animation ->
                animationPhase = animation.animatedValue as Float
                updateBarHeights()
                invalidate()
            }
        }
        
        animator?.start()
    }
    
    fun stopAnimation() {
        if (!isAnimating) return
        
        isAnimating = false
        animator?.cancel()
        animator = null
        
        // Animate bars back to minimum height
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 300
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                for (i in 0 until barCount) {
                    barHeights[i] = minBarHeight + (barHeights[i] - minBarHeight) * progress
                }
                invalidate()
            }
        }.start()
    }
    
    private fun updateBarHeights() {
        // Generate new target heights with some randomness
        if (Random.nextFloat() < 0.3f) { // Update targets occasionally
            for (i in 0 until barCount) {
                val baseHeight = sin(animationPhase * 4 + i * 0.5f) * 0.5f + 0.5f
                val randomFactor = Random.nextFloat() * 0.4f + 0.8f
                targetHeights[i] = minBarHeight + (maxBarHeight - minBarHeight) * baseHeight * randomFactor
            }
        }
        
        // Smoothly interpolate current heights towards targets
        for (i in 0 until barCount) {
            val diff = targetHeights[i] - barHeights[i]
            barHeights[i] += diff * 0.1f // Smooth interpolation
        }
    }
    
    fun setAudioLevel(level: Float) {
        if (!isAnimating) return
        
        // Use audio level to influence bar heights
        val normalizedLevel = level.coerceIn(0f, 1f)
        val scaleFactor = 0.3f + normalizedLevel * 0.7f
        
        for (i in 0 until barCount) {
            val baseHeight = sin(animationPhase * 4 + i * 0.5f) * 0.5f + 0.5f
            barHeights[i] = minBarHeight + (maxBarHeight - minBarHeight) * baseHeight * scaleFactor
        }
        
        invalidate()
    }
    
    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}