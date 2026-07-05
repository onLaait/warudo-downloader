package io.github.onlaait.warudodownloader;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.onlaait.warudodownloader.mixin.BiomeAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;

public final class ClientBiome {

    public static final Codec<Biome> DIRECT_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                            Biome.ClimateSettings.CODEC.forGetter(biome -> ((BiomeAccessor) (Object) biome).warudodownloader_getClimateSettings()),
                            BiomeSpecialEffects.CODEC.fieldOf("effects").forGetter(biome -> biome.getSpecialEffects()),
                            BiomeGenerationSettings.CODEC.forGetter(biome -> BiomeGenerationSettings.EMPTY),
                            MobSpawnSettings.CODEC.forGetter(biome -> MobSpawnSettings.EMPTY)
                    )
                    .apply(instance, (T1, T2, T3, T4) -> null)
    );
}
