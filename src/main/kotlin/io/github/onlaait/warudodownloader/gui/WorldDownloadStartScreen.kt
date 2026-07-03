package io.github.onlaait.warudodownloader.gui

import io.github.onlaait.warudodownloader.WorldDownload
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

class WorldDownloadStartScreen : Screen(TITLE) {

    private companion object {
        val TITLE = Component.translatable("warudo-downloader.start_menu")

        const val MIN_DISTANCE = 0.0
        const val MAX_DISTANCE = 32.0
        const val DEFAULT_DISTANCE = 10.0

        const val BUTTON_WIDTH = 204
    }

    private var distance = 10

    protected override fun init() {
        val textWidth = font.width(title)
        addRenderableWidget(StringWidget(width / 2 - textWidth / 2, 40, textWidth, 9, title, font))

        val distanceSlider = object : AbstractSliderButton(width / 2 - BUTTON_WIDTH / 2, height / 2 - 30, BUTTON_WIDTH, Button.DEFAULT_HEIGHT, CommonComponents.EMPTY, (DEFAULT_DISTANCE - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE)) {
            init {
                updateMessage()
            }

            override fun updateMessage() {
                message = Component.translatable("warudo-downloader.start_menu.distance").append(": ").append(Component.translatable("options.chunks", distance))
            }

            override fun applyValue() {
                distance = Mth.floor(Mth.clampedLerp(MIN_DISTANCE, MAX_DISTANCE, value))
            }
        }
        addRenderableWidget(distanceSlider)

        val startButton = Button.builder(Component.translatable("warudo-downloader.start_menu.start")) {
            WorldDownload.start(distance)
            minecraft!!.setScreen(null)
        }.pos(width / 2 - BUTTON_WIDTH / 2, height / 2 + 10)
            .width(BUTTON_WIDTH)
            .build()
        addRenderableWidget(startButton)
    }
}