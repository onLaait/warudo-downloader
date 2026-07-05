package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.util.worldupdate.WorldUpgrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.regex.Pattern;

@Mixin(WorldUpgrader.class)
public interface WorldUpgraderAccessor {

    @Accessor("REGEX")
    static Pattern getREGEX() {
        throw new AssertionError();
    }
}
