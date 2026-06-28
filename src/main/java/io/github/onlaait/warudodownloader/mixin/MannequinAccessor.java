package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mannequin.class)
public interface MannequinAccessor {

    @Invoker("setProfile")
    void warudodownloader_setProfile(ResolvableProfile profile);

    @Invoker("setDescription")
    void warudodownloader_setDescription(Component description);

    @Invoker("setHideDescription")
    void warudodownloader_setHideDescription(boolean hideDescription);
}
