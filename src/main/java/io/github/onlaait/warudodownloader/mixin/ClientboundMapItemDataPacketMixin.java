package io.github.onlaait.warudodownloader.mixin;

import io.github.onlaait.warudodownloader.WD;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundMapItemDataPacket.class)
public class ClientboundMapItemDataPacketMixin {

    @Inject(method = "applyToMap", at = @At("RETURN"))
    void injected(MapItemSavedData map, CallbackInfo ci) {
        var instance = (ClientboundMapItemDataPacket) (Object) this;
        WD.INSTANCE.handleMapData(instance.mapId(), map);
    }
}