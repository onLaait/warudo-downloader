package io.github.onlaait.warudodownloader

import io.github.onlaait.warudodownloader.command.DownloadworldCommand
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import org.slf4j.LoggerFactory

object WarudoDownloader : ClientModInitializer {

	const val MOD_ID = "warudo-downloader"

    val logger = LoggerFactory.getLogger(MOD_ID)!!

	override fun onInitializeClient() {
		logger.info("Initializing World Downloader mod")

		WD
		Minimap

		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			DownloadworldCommand.register(dispatcher)
		}
	}
}