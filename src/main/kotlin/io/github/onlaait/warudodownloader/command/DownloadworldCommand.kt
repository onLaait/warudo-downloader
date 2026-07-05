package io.github.onlaait.warudodownloader.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import io.github.onlaait.warudodownloader.WorldDownload
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.chat.Component

object DownloadworldCommand {

    private val IDLE = Component.translatable("warudo-downloader.idle")
    private val NOT_RUNNING = Component.translatable("warudo-downloader.not_running")
    private val ALREADY_RUNNING = Component.translatable("warudo-downloader.already_running")

    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            literal("downloadworld")
                .executes { ctx ->
                    ctx.source.sendFeedback(IDLE)
                    return@executes 0
                }
                .then(
                    literal("start")
                        .executes { ctx ->
                            return@executes start(ctx.source, 10)
                        }
                        .then(
                            argument("distance", IntegerArgumentType.integer(0, 32))
                                .executes { ctx ->
                                    val distance = IntegerArgumentType.getInteger(ctx, "distance")
                                    return@executes start(ctx.source, distance)
                                }
                        )

                )
                .then(
                    literal("stop")
                        .executes { ctx ->
                            if (!WorldDownload.isStarted()) {
                                ctx.source.sendError(NOT_RUNNING)
                                return@executes 0
                            }
                            WorldDownload.stop()
                            return@executes 1
                        }
                )
        )
    }

    private fun start(source: FabricClientCommandSource, distance: Int): Int {
        if (WorldDownload.isStarted()) {
            source.sendError(ALREADY_RUNNING)
            return 0
        }
        WorldDownload.start(distance)
        return 1
    }
}