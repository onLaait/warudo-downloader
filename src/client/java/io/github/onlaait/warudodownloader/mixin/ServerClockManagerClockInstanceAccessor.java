package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.world.clock.ServerClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerClockManager.ServerClockInstance.class)
public interface ServerClockManagerClockInstanceAccessor {

    @Invoker("<init>")
    static ServerClockManager.ServerClockInstance init() {
        throw new AssertionError();
    }
}
