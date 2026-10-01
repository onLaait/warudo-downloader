package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.ServerPackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DownloadedPackSource.class)
public interface DownloadedPackSourceAccessor {

    @Accessor("manager")
    ServerPackManager warudodownloader_getManager();
}
