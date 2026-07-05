package io.github.onlaait.warudodownloader.gui

import io.github.onlaait.warudodownloader.WorldDownload
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class WorldDownloadScreen : Screen(TITLE) {

    private companion object {
        val TITLE = Component.translatable("warudo-downloader.menu")

        const val BUTTON_WIDTH = 204
    }

    protected override fun init() {
        val textWidth = font.width(title)
        addRenderableWidget(StringWidget(width / 2 - textWidth / 2, 40, textWidth, 9, title, font))

        val downloading = WorldDownload.isStarted()

        val startButton = Button.builder(Component.translatable("warudo-downloader.menu.start")) {
            minecraft!!.setScreen(WorldDownloadStartScreen())
        }.pos(width / 2 - BUTTON_WIDTH / 2, height / 2 - 30)
            .width(BUTTON_WIDTH)
            .build()
        startButton.active = !downloading
        addRenderableWidget(startButton)

        val stopButton = Button.builder(Component.translatable("warudo-downloader.menu.stop")) {
            WorldDownload.stop()
            minecraft!!.setScreen(null)
        }.pos(width / 2 - BUTTON_WIDTH / 2, height / 2 + 10)
            .width(BUTTON_WIDTH)
            .build()
        stopButton.active = downloading
        addRenderableWidget(stopButton)
    }
}