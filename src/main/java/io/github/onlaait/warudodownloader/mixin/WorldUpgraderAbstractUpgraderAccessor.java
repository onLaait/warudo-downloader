package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.util.worldupdate.WorldUpgrader;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.nio.file.Path;
import java.util.List;

@Mixin(WorldUpgrader.AbstractUpgrader.class)
public interface WorldUpgraderAbstractUpgraderAccessor {

    @Invoker("getAllChunkPositions")
    static List<WorldUpgrader.FileToUpgrade> getAllChunkPositions(RegionStorageInfo regionStorageInfo, Path path) {
        throw new AssertionError();
    }
}