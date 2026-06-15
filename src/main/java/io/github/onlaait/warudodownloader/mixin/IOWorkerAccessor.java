package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.nio.file.Path;

@Mixin(IOWorker.class)
public interface IOWorkerAccessor {

    @Invoker("<init>")
    static IOWorker init(RegionStorageInfo regionStorageInfo, Path path, boolean bl) {
        throw new AssertionError();
    }
}
