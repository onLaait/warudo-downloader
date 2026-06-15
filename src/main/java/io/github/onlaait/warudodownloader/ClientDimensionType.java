package io.github.onlaait.warudodownloader;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.Collections;

public final class ClientDimensionType {

    public static final Codec<DimensionType> DIRECT_CODEC = createDirectCodec(EnvironmentAttributeMap.NETWORK_CODEC);

    private static Codec<DimensionType> createDirectCodec(Codec<EnvironmentAttributeMap> codec) {
        return ExtraCodecs.catchDecoderException(
                RecordCodecBuilder.create(
                        instance -> instance.group(
                                        Codec.BOOL.optionalFieldOf("has_fixed_time", false).forGetter(DimensionType::hasFixedTime),
                                        Codec.BOOL.fieldOf("has_skylight").forGetter(DimensionType::hasSkyLight),
                                        Codec.BOOL.fieldOf("has_ceiling").forGetter(DimensionType::hasCeiling),
                                        Codec.doubleRange(1.0E-5F, 3.0E7).fieldOf("coordinate_scale").forGetter(DimensionType::coordinateScale),
                                        Codec.intRange(DimensionType.MIN_Y, DimensionType.MAX_Y).fieldOf("min_y").forGetter(DimensionType::minY),
                                        Codec.intRange(16, DimensionType.Y_SIZE).fieldOf("height").forGetter(DimensionType::height),
                                        Codec.intRange(0, DimensionType.Y_SIZE).fieldOf("logical_height").forGetter(DimensionType::logicalHeight),
                                        TagKey.hashedCodec(Registries.BLOCK).fieldOf("infiniburn").forGetter(DimensionType::infiniburn),
                                        Codec.FLOAT.fieldOf("ambient_light").forGetter(DimensionType::ambientLight),
                                        DimensionType.MonsterSettings.CODEC.forGetter(DimensionType::monsterSettings),
                                        DimensionType.Skybox.CODEC.optionalFieldOf("skybox", DimensionType.Skybox.OVERWORLD).forGetter(DimensionType::skybox),
                                        DimensionType.CardinalLightType.CODEC
                                                .optionalFieldOf("cardinal_light", DimensionType.CardinalLightType.DEFAULT)
                                                .forGetter(DimensionType::cardinalLightType),
                                        codec.optionalFieldOf("attributes", EnvironmentAttributeMap.EMPTY).forGetter(DimensionType::attributes),
                                        Codec.STRING.listOf().optionalFieldOf("timelines", Collections.emptyList()).forGetter(dimensionType -> dimensionType.timelines().stream().map(a -> a.unwrapKey().get().identifier().toString()).toList())
                                )
                                .apply(instance, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14) -> null)
                )
        );
    }
}
