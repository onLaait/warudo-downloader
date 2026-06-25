package io.github.onlaait.warudodownloader.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import io.github.onlaait.warudodownloader.WD
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.chat.Component

object DownloadworldCommand {

    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            literal("downloadworld")
                .executes { ctx ->
                    ctx.source.sendFeedback(Component.literal("Warudo Downloader is here."))
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
                            if (!WD.isStarted()) {
                                ctx.source.sendError(Component.literal("Not downloading the world."))
                                return@executes 0
                            }
                            WD.stop()
                            return@executes 1
                        }
                )
        )
    }

    private fun start(source: FabricClientCommandSource, distance: Int): Int {
        if (WD.isStarted()) {
            source.sendError(Component.literal("Already downloading the world."))
            return 0
        }
        WD.start(distance)
        return 1
    }
}