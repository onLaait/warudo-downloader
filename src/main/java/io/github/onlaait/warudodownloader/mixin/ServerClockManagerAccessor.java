package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.clock.PackedClockStates;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(ServerClockManager.class)
public interface ServerClockManagerAccessor {

    @Invoker("<init>")
    static ServerClockManager init(PackedClockStates packedClockStates) {
        throw new AssertionError();
    }

    @Accessor("clocks")
    Map<Holder<WorldClock>, ServerClockManager.ClockInstance> warudodownloader_getClocks();
}
