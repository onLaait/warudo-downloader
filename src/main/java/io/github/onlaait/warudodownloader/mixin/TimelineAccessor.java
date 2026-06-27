package io.github.onlaait.warudodownloader.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.timeline.AttributeTrack;
import net.minecraft.world.timeline.Timeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(Timeline.class)
public interface TimelineAccessor {

    @Accessor("TRACKS_CODEC")
    static Codec<Map<EnvironmentAttribute<?>, AttributeTrack<?, ?>>> getTRACKS_CODEC() {
        throw new AssertionError();
    }

    @Accessor("tracks")
    Map<EnvironmentAttribute<?>, AttributeTrack<?, ?>> warudodownloader_getTracks();

    @Accessor("timeMarkers")
    Map<ResourceKey<ClockTimeMarker>, Timeline.TimeMarkerInfo> warudodownloader_getTimeMarkers();

    @Invoker("validateInternal")
    static DataResult<Timeline> validateInternal(final Timeline timeline) {
        throw new AssertionError();
    }

    @Invoker("filterSyncableTracks")
    static Timeline filterSyncableTracks(final Timeline timeline) {
        throw new AssertionError();
    }
}
