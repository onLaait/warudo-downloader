package io.github.onlaait.warudodownloader

import com.mojang.blaze3d.platform.InputConstants
import io.github.onlaait.warudodownloader.gui.WorldDownloadScreen
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW

object Keys {

    val CATEGORY = KeyMapping.Category.register(WarudoDownloader.id("main"))

    val keyOpenMenu = KeyBindingHelper.registerKeyBinding(
        KeyMapping(
            "key.warudo-downloader.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY
        )
    )

    init {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            while (keyOpenMenu.consumeClick()) {
                if (mc.level != null) {
                    mc.setScreen(WorldDownloadScreen())
                }
            }
        }
    }
}