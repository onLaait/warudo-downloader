package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.nio.file.Path;

@Mixin(LevelStorageSource.class)
public interface LevelStorageSourceAccessor {

    @Invoker("getLevelPath")
    Path warudodownloader_getLevelPath(String string);
}
