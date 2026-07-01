package io.github.onlaait.warudodownloader.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {

    @Invoker("getEntities")
    LevelEntityGetter<Entity> warudodownloader_getEntities();

    @Invoker("getAllMapData")
    Map<MapId, MapItemSavedData> warudodownloader_getAllMapData();
}
