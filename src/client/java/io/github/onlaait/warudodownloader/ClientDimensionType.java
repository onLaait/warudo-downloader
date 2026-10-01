package io.github.onlaait.warudodownloader;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.Collections;
import java.util.List;

public final class ClientDimensionType {

    public static final Codec<DimensionType> DIRECT_CODEC = createDirectCodec(EnvironmentAttributeMap.NETWORK_CODEC);

    private static Codec<DimensionType> createDirectCodec(final Codec<EnvironmentAttributeMap> attributeMapCodec) {
        return ExtraCodecs.catchDecoderException(
                RecordCodecBuilder.create(
                        i -> i.group(
                                        Codec.BOOL.optionalFieldOf("has_fixed_time", false).forGetter(DimensionType::hasFixedTime),
                                        Codec.BOOL.fieldOf("has_skylight").forGetter(DimensionType::hasSkyLight),
                                        Codec.BOOL.fieldOf("has_ceiling").forGetter(DimensionType::hasCeiling),
                                        Codec.BOOL.fieldOf("has_ender_dragon_fight").forGetter(DimensionType::hasEnderDragonFight),
                                        Codec.doubleRange(1.0E-5F, 3.0E7).fieldOf("coordinate_scale").forGetter(DimensionType::coordinateScale),
                                        Codec.intRange(DimensionType.MIN_Y, DimensionType.MAX_Y).fieldOf("min_y").forGetter(DimensionType::minY),
                                        Codec.intRange(16, DimensionType.Y_SIZE).fieldOf("height").forGetter(DimensionType::height),
                                        Codec.intRange(0, DimensionType.Y_SIZE).fieldOf("logical_height").forGetter(DimensionType::logicalHeight),
                                        ExtraCodecs.compactListCodec(Codec.STRING).fieldOf("infiniburn").forGetter(d -> {
                                            var infiniburn = d.infiniburn();
                                            return infiniburn.unwrapKey().map(t -> List.of("#" + t.location())).orElseGet(() -> infiniburn.stream().map(h -> h.unwrapKey().get().identifier().toString()).toList());
                                        }),
                                        Codec.FLOAT.fieldOf("ambient_light").forGetter(DimensionType::ambientLight),
                                        DimensionType.MonsterSettings.CODEC.forGetter(DimensionType::monsterSettings),
                                        DimensionType.Skybox.CODEC.optionalFieldOf("skybox", DimensionType.Skybox.OVERWORLD).forGetter(DimensionType::skybox),
                                        CardinalLighting.Type.CODEC.optionalFieldOf("cardinal_light", CardinalLighting.Type.DEFAULT).forGetter(DimensionType::cardinalLightType),
                                        attributeMapCodec.optionalFieldOf("attributes", EnvironmentAttributeMap.EMPTY).forGetter(DimensionType::attributes),
                                        ExtraCodecs.compactListCodec(Codec.STRING).optionalFieldOf("timelines", Collections.emptyList()).forGetter(d -> d.timelines().stream().map(h -> h.unwrapKey().get().identifier().toString()).toList()),
                                        Codec.STRING.optionalFieldOf("default_clock").forGetter(d -> d.defaultClock().map(_ -> "minecraft:overworld"))
                                )
                                .apply(i, (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) -> null)
                )
        );
    }
}
