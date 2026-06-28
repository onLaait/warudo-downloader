package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.util.worldupdate.FileToUpgrade;
import net.minecraft.util.worldupdate.RegionStorageUpgrader;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.nio.file.Path;
import java.util.List;

@Mixin(RegionStorageUpgrader.class)
public interface RegionStorageUpgraderAccessor {

    @Invoker("getAllChunkPositions")
    static List<FileToUpgrade> getAllChunkPositions(RegionStorageInfo info, Path regionFolder) {
        throw new AssertionError();
    }
}
