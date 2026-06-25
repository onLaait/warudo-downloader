package io.github.onlaait.warudodownloader;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.onlaait.warudodownloader.mixin.BiomeAccessor;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;

public class ClientBiome {

    public static final Codec<Biome> DIRECT_CODEC = RecordCodecBuilder.create(
            i -> i.group(
                            Biome.ClimateSettings.CODEC.forGetter(b -> ((BiomeAccessor) (Object) b).warudodownloader$getClimateSettings()),
                            EnvironmentAttributeMap.CODEC_ONLY_POSITIONAL.optionalFieldOf("attributes", EnvironmentAttributeMap.EMPTY).forGetter(b -> b.getAttributes()),
                            BiomeSpecialEffects.CODEC.fieldOf("effects").forGetter(b -> b.getSpecialEffects()),
                            BiomeGenerationSettings.CODEC.forGetter(b -> BiomeGenerationSettings.EMPTY),
                            MobSpawnSettings.CODEC.forGetter(b -> MobSpawnSettings.EMPTY)
                    )
                    .apply(i, (_, _, _, _, _) -> null)
    );
}
