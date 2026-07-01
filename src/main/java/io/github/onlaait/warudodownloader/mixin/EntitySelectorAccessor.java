package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntitySelector.class)
public interface EntitySelectorAccessor {

    @Accessor("ANY_TYPE")
    static EntityTypeTest<Entity, ?> getANY_TYPE() {
        throw new AssertionError();
    }
}
