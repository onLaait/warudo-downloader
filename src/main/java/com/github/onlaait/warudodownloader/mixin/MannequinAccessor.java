package com.github.onlaait.warudodownloader.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mannequin.class)
public interface MannequinAccessor {

    @Invoker("setProfile")
    void warudodownloader$setProfile(ResolvableProfile resolvableProfile);

    @Invoker("setDescription")
    void warudodownloader$setDescription(Component component);

    @Invoker("setHideDescription")
    void warudodownloader$setHideDescription(boolean bl);
}
