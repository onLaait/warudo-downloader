package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientClockManager.ClockInstance.class)
public interface ClientClockManagerClockInstanceAccessor {

    @Accessor("totalTicks")
    long warudodownloader_getTotalTicks();

    @Accessor("partialTick")
    float warudodownloader_getPartialTick();
}
