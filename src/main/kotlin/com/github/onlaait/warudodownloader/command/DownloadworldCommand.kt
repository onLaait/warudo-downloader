package com.github.onlaait.warudodownloader.command

import com.github.onlaait.warudodownloader.WD
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.chat.Component

object DownloadworldCommand {

    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            literal("downloadworld")
                .executes { ctx ->
                    return@executes 0
                }
                .then(
                    literal("start")
                        .executes { ctx ->
                            return@executes start(ctx.source, 16)
                        }
                        .then(
                            argument("range", IntegerArgumentType.integer(0, 256))
                                .executes { ctx ->
                                    val range = IntegerArgumentType.getInteger(ctx, "range")
                                    return@executes start(ctx.source, range)
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

    private fun start(source: FabricClientCommandSource, range: Int): Int {
        if (WD.isStarted()) {
            source.sendError(Component.literal("Already downloading the world."))
            return 0
        }
        WD.start(range)
        return 1
    }
}