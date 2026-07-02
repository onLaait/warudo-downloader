package io.github.onlaait.warudodownloader.gui

import io.github.onlaait.warudodownloader.WarudoDownloader
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.max
import kotlin.math.min

class Minimap {

    companion object {
        private const val SIZE = 100
        private const val MAX_DISTANCE: Int = SIZE * 1

        var instance: Minimap? = null

        private val hudLayer = HudElement { graphics, _ ->
            val minimap = instance ?: return@HudElement
            if (Minecraft.getInstance().debugEntries.isF3Visible) return@HudElement
            minimap.draw(graphics)
        }

        init {
            HudElementRegistry.addLast(WarudoDownloader.id("minimap"), hudLayer)
        }
    }

    private val pixels = mutableSetOf<Pair<Int, Int>>()
    private var changed = false
    private val pixelMap = sortedMapOf<Int, MutableList<Int>>()
    private var minX = 0
    private var maxX = 0
    private var minY = 0
    private var maxY = 0
    private var scale = 0f

    var playerPrevX = 0
    var playerPrevY = 0
    var playerX = 0
    var playerY = 0

    fun init() {
        instance = this
    }

    fun addPixel(x: Int, y: Int) {
        if (pixels.add(x to y)) changed = true
    }

    fun draw(graphics: GuiGraphics) {
        if (playerPrevX != playerX || playerPrevY != playerY) {
            changed = true
            playerPrevX = playerX
            playerPrevY = playerY
        }
        if (changed) {
            changed = false

            val iter = pixels.iterator()
            val first = iter.next()
            minX = first.first
            maxX = first.first
            minY = first.second
            maxY = first.second
            for ((x, y) in iter) {
                if (minX > x) {
                    minX = x
                } else if (maxX < x) {
                    maxX = x
                }
                if (minY > y) {
                    minY = y
                } else if (maxY < y) {
                    maxY = y
                }
            }
            if (minX > playerX) minX = playerX
            else if (maxX < playerX) maxX = playerX
            if (minY > playerY) minY = playerY
            else if (maxY < playerY) maxY = playerY

            var viewMinX = playerX - MAX_DISTANCE
            var viewMinY = playerY - MAX_DISTANCE
            var viewMaxX = playerX + MAX_DISTANCE
            var viewMaxY = playerY + MAX_DISTANCE
            if (minX < viewMinX && maxX < viewMaxX) {
                val d = viewMaxX - maxX
                viewMinX -= d
                viewMaxX -= d
            } else if (viewMaxX < maxX && viewMinX < minX) {
                val d = minX - viewMinX
                viewMinX += d
                viewMaxX += d
            }
            if (minY < viewMinY && maxY < viewMaxY) {
                val d = viewMaxY - maxY
                viewMinY -= d
                viewMaxY -= d
            } else if (viewMaxY < maxY && viewMinY < minY) {
                val d = minY - viewMinY
                viewMinY += d
                viewMaxY += d
            }
            minX = max(minX, viewMinX)
            minY = max(minY, viewMinY)
            maxX = min(maxX, viewMaxX)
            maxY = min(maxY, viewMaxY)

            pixelMap.clear()
            val xRan = minX..maxX
            val yRan = minY..maxY
            for ((x, y) in pixels) {
                if (x in xRan && y in yRan) pixelMap.getOrPut(x) { mutableListOf() } += y
            }
            pixelMap.forEach { it.value.sort() }

            val width = maxX - minX + 1
            val height = maxY - minY + 1
            val res = maxOf(SIZE, width, height)

            val offsetX = (res - width) / 2
            val offsetY = (res - height) / 2
            minX -= offsetX
            maxX += offsetX + (res - width) % 2
            minY -= offsetY
            maxY += offsetY + (res - height) % 2

            scale = SIZE / res.toFloat()
        }

        val black = -16777216
        val white = -1
        val red = -65536

        val matrices = graphics.pose()
        matrices.pushMatrix()
        matrices.translation((graphics.guiWidth() - SIZE).toFloat(), 0f)

        graphics.fill(0, 0, SIZE, SIZE, white)

        if (pixels.isNotEmpty()) {
            fun fill(x1: Int, y1: Int, x2: Int, y2: Int, color: Int = black) {
                val drawX1 = ((x1 - minX) * scale).toInt()
                val drawY1 = ((y1 - minY) * scale).toInt()
                val drawX2 = ((x2 - minX) * scale).toInt()
                val drawY2 = ((y2 - minY) * scale).toInt()
                graphics.fill(drawX1, drawY1, drawX2 + 1, drawY2 + 1, color)
            }

            var nextX = minX
            for ((x, ys) in pixelMap) {
                if (nextX != x) {
                    val x1 = nextX
                    val y1 = minY
                    val x2 = x - 1
                    val y2 = maxY
                    fill(x1, y1, x2, y2)
                }
                nextX = x + 1

                var nextY = minY
                for (y in ys) {
                    if (nextY != y) {
                        val x1 = x
                        val y1 = nextY
                        val x2 = x
                        val y2 = y - 1
                        fill(x1, y1, x2, y2)
                    }
                    nextY = y + 1
                }
                if (nextY - 1 != maxY) {
                    val x1 = x
                    val y1 = nextY
                    val x2 = x
                    val y2 = maxY
                    fill(x1, y1, x2, y2)
                }
            }
            if (nextX - 1 != maxX) {
                val x1 = nextX
                val y1 = minY
                val x2 = maxX
                val y2 = maxY
                fill(x1, y1, x2, y2)
            }

            run {
                val x = playerX
                val y = playerY
                val drawX = ((x - minX) * scale).toInt()
                val drawY = ((y - minY) * scale).toInt()
                graphics.fill(drawX - 1, drawY, drawX + 1 + 1, drawY + 1, red)
                graphics.fill(drawX, drawY - 1, drawX + 1, drawY + 1 + 1, red)
            }
        }

        matrices.popMatrix()
    }

    fun dispose() {
        instance = null
    }
}