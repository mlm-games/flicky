package app.flicky.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Central configuration for all animations in the app (similar to github's kotlin animation repos)
 */
object AnimationConfig {

    // Standard durations
    const val QUICK = 150
    const val SUB_QUICK = 200
    const val STANDARD = 300
    const val SLOW = 500
    const val EXTRA_SLOW = 1000

    // Standard easing
    val standardEasing = FastOutSlowInEasing
    val emphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    // Reusable animation specs
    val quickTween = tween<Float>(QUICK, easing = standardEasing)
    val standardTween = tween<Float>(STANDARD, easing = standardEasing)
    val slowTween = tween<Float>(SLOW, easing = standardEasing)

    val standardIntTween = tween<IntSize>(STANDARD, easing = standardEasing)


    // List item animations
    val listItemAnimationSpec = tween<IntOffset>(STANDARD, easing = standardEasing)
    val listItemFadeSpec = tween<Float>(QUICK, easing = standardEasing)

    // Navigation transitions
    object Navigation {

        fun slideUpTransition() = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(STANDARD, easing = standardEasing)
        ) togetherWith slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(STANDARD, easing = standardEasing)
        )

        fun slideDownTransition() = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(STANDARD, easing = standardEasing)
        ) togetherWith slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(STANDARD, easing = standardEasing)
        )

        fun slideLeftTransition() = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(STANDARD, easing = standardEasing)
        ) togetherWith slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(STANDARD, easing = standardEasing)
        )

        fun slideRightTransition() = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(STANDARD, easing = standardEasing)
        ) togetherWith slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(STANDARD, easing = standardEasing)
        )

        fun fadeTransition() = fadeIn(
            animationSpec = tween(STANDARD, easing = standardEasing)
        ) togetherWith fadeOut(
            animationSpec = tween(STANDARD, easing = standardEasing)
        )

        fun scaleAndFadeTransition() = (
                fadeIn(animationSpec = tween(STANDARD)) +
                        scaleIn(initialScale = 0.95f, animationSpec = tween(STANDARD))
                ) togetherWith (
                fadeOut(animationSpec = tween(STANDARD)) +
                        scaleOut(targetScale = 0.95f, animationSpec = tween(STANDARD))
                )

        fun widgetPickerTransition() = (
                slideInHorizontally(
                    initialOffsetX = { it / 5 },
                    animationSpec = tween(STANDARD)
                ) + fadeIn(animationSpec = tween(STANDARD)) +
                        scaleIn(initialScale = 0.95f, animationSpec = tween(STANDARD))
                ) togetherWith (
                slideOutHorizontally(
                    targetOffsetX = { -it / 5 },
                    animationSpec = tween(STANDARD)
                ) + fadeOut(animationSpec = tween(STANDARD)) +
                        scaleOut(targetScale = 0.95f, animationSpec = tween(STANDARD))
                )
    }

    fun contentSizeAnimationSpec() = tween<IntSize>(STANDARD, easing = standardEasing)

    // Visibility animations
    fun enterTransition() = fadeIn(animationSpec = tween(QUICK)) +
            expandVertically(animationSpec = tween(STANDARD))

    fun exitTransition() = fadeOut(animationSpec = tween(QUICK)) +
            shrinkVertically(animationSpec = tween(STANDARD))
}

fun getAdjustedDuration(baseDuration: Int, speedMultiplier: Float): Int {
    return (baseDuration / speedMultiplier).toInt().coerceAtLeast(1)
}