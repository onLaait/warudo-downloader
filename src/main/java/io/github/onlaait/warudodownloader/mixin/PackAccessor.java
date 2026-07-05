package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Pack.class)
public interface PackAccessor {

    @Accessor("resources")
    Pack.ResourcesSupplier warudodownloader_getResources();
}
