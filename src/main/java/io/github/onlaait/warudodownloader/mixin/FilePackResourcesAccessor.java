package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.server.packs.FilePackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

@Mixin(FilePackResources.class)
public interface FilePackResourcesAccessor {

    @Accessor("file")
    File warudodownloader_getFile();
}
