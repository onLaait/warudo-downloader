package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.world.clock.ServerClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerClockManager.ClockInstance.class)
public interface ServerClockManagerClockInstanceAccessor {

    @Invoker("<init>")
    static ServerClockManager.ClockInstance init() {
        throw new AssertionError();
    }
}
