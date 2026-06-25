package io.github.onlaait.warudodownloader;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.onlaait.warudodownloader.mixin.TimelineAccessor;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.timeline.Timeline;

import java.util.Map;

public class ClientTimeline {

    public static final Codec<Timeline> DIRECT_CODEC = RecordCodecBuilder.<Timeline>create(
                    i -> i.group(
                                    Codec.STRING.fieldOf("clock").forGetter(_ -> "minecraft:overworld"),
                                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("period_ticks").forGetter(Timeline::periodTicks),
                                    TimelineAccessor.getTRACKS_CODEC().optionalFieldOf("tracks", Map.of()).forGetter(t -> ((TimelineAccessor) t).warudodownloader$getTracks()),
                                    Codec.unboundedMap(ClockTimeMarker.KEY_CODEC, Timeline.TimeMarkerInfo.CODEC).optionalFieldOf("time_markers", Map.of()).forGetter(t -> ((TimelineAccessor) t).warudodownloader$getTimeMarkers())
                            )
                            .apply(i, (_, _, _, _) -> null)
            )
            .validate(TimelineAccessor::validateInternal)
            .xmap(TimelineAccessor::filterSyncableTracks, TimelineAccessor::filterSyncableTracks);
}
