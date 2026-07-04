package io.github.onlaait.warudodownloader

import io.github.onlaait.warudodownloader.command.DownloadworldCommand
import io.github.onlaait.warudodownloader.gui.Minimap
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

object WarudoDownloader : ClientModInitializer {

	const val MOD_ID = "warudo-downloader"

	val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitializeClient() {
		LOGGER.info("Initializing Warudo Downloader mod")

		Keys
		Minimap
		WorldDownload

		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			DownloadworldCommand.register(dispatcher)
		}
	}

	fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
}
