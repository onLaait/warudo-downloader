package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientClockManager.class)
public interface ClientClockManagerAccessor {

    @Invoker("getInstance")
    ClientClockManager.ClockInstance warudodownloader_getInstance(Holder<WorldClock> definition);
}
