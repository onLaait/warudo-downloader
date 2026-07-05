package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityStorage.class)
public interface EntityStorageAccessor {

    @Invoker("writeChunkPos")
    static void writeChunkPos(CompoundTag compoundTag, ChunkPos chunkPos) {
        throw new AssertionError();
    }
}
