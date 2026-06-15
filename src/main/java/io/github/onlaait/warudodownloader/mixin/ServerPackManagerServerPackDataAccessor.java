package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.client.resources.server.ServerPackManager;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.file.Path;

@Mixin(ServerPackManager.ServerPackData.class)
public interface ServerPackManagerServerPackDataAccessor {

    @Accessor("path")
    @Nullable
    Path warudodownloader$getPath();
}
