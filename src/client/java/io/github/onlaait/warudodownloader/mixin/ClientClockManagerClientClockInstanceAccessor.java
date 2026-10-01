package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientClockManager.ClientClockInstance.class)
public interface ClientClockManagerClientClockInstanceAccessor {

    @Accessor("totalTicks")
    long warudodownloader_getTotalTicks();

    @Accessor("partialTick")
    float warudodownloader_getPartialTick();
}
