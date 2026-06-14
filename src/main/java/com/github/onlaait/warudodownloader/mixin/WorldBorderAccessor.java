package com.github.onlaait.warudodownloader.mixin;

import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldBorder.class)
public interface WorldBorderAccessor {

    @Accessor("settings")
    WorldBorder.Settings warudodownloader$getSettings();
}