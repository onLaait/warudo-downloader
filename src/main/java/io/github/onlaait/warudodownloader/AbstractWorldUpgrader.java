package io.github.onlaait.warudodownloader;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.github.onlaait.warudodownloader.mixin.WorldUpgraderAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// FROM net.minecraft.util.worldupdate.WorldUpgrader
public final class AbstractWorldUpgrader {

    private final LevelStorageSource.LevelStorageAccess levelStorage;
    private static final Pattern REGEX = WorldUpgraderAccessor.getREGEX();

    public AbstractWorldUpgrader(LevelStorageSource.LevelStorageAccess levelStorageAccess) {
        this.levelStorage = levelStorageAccess;
    }

    public List<ChunkPos> getAllChunkPos(ResourceKey<Level> resourceKey) {
        File file = this.levelStorage.getDimensionPath(resourceKey).toFile();
        File file2 = new File(file, "region");
        File[] files = file2.listFiles((filex, string) -> string.endsWith(".mca"));
        if (files == null) {
            return ImmutableList.of();
        }

        List<ChunkPos> list = Lists.newArrayList();

        for (File file3 : files) {
            Matcher matcher = REGEX.matcher(file3.getName());
            if (matcher.matches()) {
                int i = Integer.parseInt(matcher.group(1)) << 5;
                int j = Integer.parseInt(matcher.group(2)) << 5;

                try (RegionFile regionFile = new RegionFile(file3.toPath(), file2.toPath(), true)) {
                    for (int k = 0; k < 32; k++) {
                        for (int l = 0; l < 32; l++) {
                            ChunkPos chunkPos = new ChunkPos(k + i, l + j);
                            if (regionFile.doesChunkExist(chunkPos)) {
                                list.add(chunkPos);
                            }
                        }
                    }
                } catch (Throwable var19) {
                }
            }
        }

        return list;
    }
}
