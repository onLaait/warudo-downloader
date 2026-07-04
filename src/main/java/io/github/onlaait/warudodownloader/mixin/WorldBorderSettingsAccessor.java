package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WorldBorder.Settings.class)
public interface WorldBorderSettingsAccessor {

    @Invoker("<init>")
    static WorldBorder.Settings init(WorldBorder worldBorder) {
        throw new AssertionError();
    }
}
