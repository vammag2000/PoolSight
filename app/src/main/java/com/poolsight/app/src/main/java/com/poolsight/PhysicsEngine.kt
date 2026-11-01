package com.poolsight

import android.graphics.PointF
import kotlin.math.*

class PhysicsEngine(
    private val tableWidth: Float,
    private val tableHeight: Float,
    private val ballRadius: Float
) {

    companion object {
        const val RESTITUTION_BALL = 0.95f
        const val RESTITUTION_WALL = 0.75f
        const val FRICTION_PER_SEC = 0.4f
        const val POCKET_RADIUS_FACTOR = 2.2f
        const val SIM_DT = 1f / 120f
        const val MAX_SIM_TIME = 6f
        const val MIN_SPEED = 0.5f
    }

    private data class SimBall(
        var x: Float, var y: Float,
        val r: Float,
        val color: BallColor,
        var vx: Float = 0f, var vy: Float = 0f,
        var active: Boolean = true
    )

    private var balls: List<SimBall> = emptyList()
    private var pockets: List<PointF> = emptyList()
    private var offsetX = 0f
    private var offsetY = 0f

    fun setBalls(
        detected: List<BallDetected>,
        pockets: List<PointF>,
        offsetX: Float,
        offsetY: Float
    ) {
        this.offsetX = offsetX
        this.offsetY = offsetY
        this.pockets = pockets.map { PointF(it.x - offsetX, it.y - offsetY) }
        this.balls = detected.map {
            SimBall(
                x = it.center.x - offsetX,
                y = it.center.y - offsetY,
                r = it.radius,
                color = it.color
            )
        }
    }

    fun predictAllShots(
        cueBall: BallDetected,
        allBalls: List<BallDetected>,
        aimAngleRad: Float?
    ): List<PointF> {
        val angle = aimAngleRad ?: return emptyList()

        val cueSim = balls.firstOrNull { it.color == BallColor.CUE } ?: return emptyList()
        val direction = PointF(cos(angle), sin(angle))

        val trajectory = mutableListOf<PointF>()
        trajectory.add(PointF(cueSim.x + offsetX, cueSim.y + offsetY))

        var cx = cueSim.x
        var cy = cueSim.y
        var vx = direction.x
        var vy = direction.y
        val speed = 1f

        var t = 0f
        var hitBall: SimBall? = null
        var hitPocket: PointF? = null

        while (t < MAX_SIM_TIME) {
            val stepX = vx * speed * SIM_DT
            val stepY = vy * speed * SIM_DT
            cx += stepX; cy += stepY

            if (cx - ballRadius < 0 || cx + ballRadius > tableWidth ||
                cy - ballRadius < 0 || cy + ballRadius > tableHeight) {
                trajectory.add(PointF(cx + offsetX, cy + offsetY))
                break
            }

            hitBall = balls.firstOrNull { other ->
                other !== cueSim && other.active &&
                hypot(other.x - cx, other.y - cy) <= ballRadius + other.r
            }
            if (hitBall != null) {
                trajectory.add(PointF(hitBall.x + offsetX, hitBall.y + offsetY))
                break
            }

            hitPocket = pockets.firstOrNull { p ->
                hypot(p.x - cx, p.y - cy) <= ballRadius * POCKET_RADIUS_FACTOR
            }
            if (hitPocket != null) {
                trajectory.add(PointF(hitPocket.x + offsetX, hitPocket.y + offsetY))
                break
            }

            t += SIM_DT
        }

        if (hitBall != null) {
            val impactPoint = trajectory.last()
            val dx = hitBall.x - (impactPoint.x - offsetX)
            val dy = hitBall.y - (impactPoint.y - offsetY)
            val len = hypot(dx, dy).coerceAtLeast(0.001f)

            val targetVx = dx / len
            val targetVy = dy / len

            val targetTraj = simulateBallPath(
                startX = hitBall.x,
                startY = hitBall.y,
                vx = targetVx,
                vy = targetVy,
                ignoreBall = cueSim
            )
            trajectory.addAll(targetTraj)
        }

        return trajectory
    }

    private fun simulateBallPath(
        startX: Float, startY: Float,
        vx: Float, vy: Float,
        ignoreBall: SimBall?
    ): List<PointF> {
        val result = mutableListOf<PointF>()
        var x = startX; var y = startY
        var dx = vx; var dy = vy
        var t = 0f

        while (t < MAX_SIM_TIME) {
            x += dx * SIM_DT; y += dy * SIM_DT

            if (x - ballRadius < 0) { x = ballRadius; dx = -dx * RESTITUTION_WALL }
            if (x + ballRadius > tableWidth) { x = tableWidth - ballRadius; dx = -dx * RESTITUTION_WALL }
            if (y - ballRadius < 0) { y = ballRadius; dy = -dy * RESTITUTION_WALL }
            if (y + ballRadius > tableHeight) { y = tableHeight - ballRadius; dy = -dy * RESTITUTION_WALL }

            result.add(PointF(x + offsetX, y + offsetY))

            val pocket = pockets.firstOrNull { p ->
                hypot(p.x - x, p.y - y) <= ballRadius * POCKET_RADIUS_FACTOR
            }
            if (pocket != null) {
                result.add(PointF(pocket.x + offsetX, pocket.y + offsetY))
                break
            }

            val decay = (1f - FRICTION_PER_SEC * SIM_DT).coerceIn(0f, 1f)
            dx *= decay; dy *= decay

            if (hypot(dx, dy) < MIN_SPEED * SIM_DT) break
            t += SIM_DT
        }
        return result
    }
}
