package io.github.onlaait.warudodownloader

import io.github.onlaait.warudodownloader.command.DownloadworldCommand
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object WarudoDownloader : ClientModInitializer {

	const val MOD_ID = "warudo-downloader"

    val logger = LoggerFactory.getLogger(MOD_ID)!!

	override fun onInitializeClient() {
		logger.info("Initializing World Downloader mod")

		WD.init()

		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			DownloadworldCommand.register(dispatcher)
		}

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "world_canvas"), WorldCanvas.hudLayer)
	}
}