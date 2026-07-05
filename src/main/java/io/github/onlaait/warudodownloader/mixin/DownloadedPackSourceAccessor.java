package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.client.resources.DownloadedPackSource;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DownloadedPackSource.class)
public interface DownloadedPackSourceAccessor {

    @Accessor("serverPack")
    Pack warudodownloader_getServerPack();
}
