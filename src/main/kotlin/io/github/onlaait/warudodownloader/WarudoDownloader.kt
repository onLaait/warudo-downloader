package io.github.onlaait.warudodownloader

import io.github.onlaait.warudodownloader.command.DownloadworldCommand
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object WarudoDownloader : ClientModInitializer {
	const val MOD_ID: String = "warudo-downloader"

	val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitializeClient() {
		LOGGER.info("Initializing World Downloader mod")

		WD
		Minimap

		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			DownloadworldCommand.register(dispatcher)
		}
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
