package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.server.packs.FilePackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

@Mixin(FilePackResources.FileResourcesSupplier.class)
public interface FilePackResourcesFileResourcesSupplierAccessor {

    @Accessor("content")
    File warudodownloader_getContent();
}
