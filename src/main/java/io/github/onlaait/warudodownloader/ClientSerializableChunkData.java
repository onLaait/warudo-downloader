package io.github.onlaait.warudodownloader;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.Optionull;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.*;

public class ClientSerializableChunkData {

    // FROM net.minecraft.world.level.chunk.storage.SerializableChunkData.copyOf
    public static SerializableChunkData copyOf(final ClientLevel level, final ChunkAccess chunk) {
        if (!chunk.canBeSerialized()) {
            throw new IllegalArgumentException("Chunk can't be serialized: " + chunk);
        }

        ChunkPos pos = chunk.getPos();
        List<SerializableChunkData.SectionData> sectionData = new ArrayList<>();
        LevelChunkSection[] chunkSections = chunk.getSections();
        LevelLightEngine lightEngine = level.getChunkSource().getLightEngine();

        for (int sectionY = lightEngine.getMinLightSection(); sectionY < lightEngine.getMaxLightSection(); sectionY++) {
            int sectionIndex = chunk.getSectionIndexFromSectionY(sectionY);
            boolean hasSection = sectionIndex >= 0 && sectionIndex < chunkSections.length;
            DataLayer sourceBlockLight = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(pos, sectionY));
            DataLayer sourceSkyLight = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(pos, sectionY));
            DataLayer blockLight = sourceBlockLight != null && !sourceBlockLight.isEmpty() ? sourceBlockLight.copy() : null;
            DataLayer skyLight = sourceSkyLight != null && !sourceSkyLight.isEmpty() ? sourceSkyLight.copy() : null;
            if (hasSection || blockLight != null || skyLight != null) {
                LevelChunkSection section = hasSection ? chunkSections[sectionIndex].copy() : null;
                sectionData.add(new SerializableChunkData.SectionData(sectionY, section, blockLight, skyLight));
            }
        }

        List<CompoundTag> blockEntities = new ArrayList<>(chunk.getBlockEntitiesPos().size());

        for (BlockPos blockPos : chunk.getBlockEntitiesPos()) {
            CompoundTag blockEntityTag = chunk.getBlockEntityNbtForSaving(blockPos, level.registryAccess());
            if (blockEntityTag != null) {
                blockEntities.add(blockEntityTag);
            }
        }

        List<CompoundTag> entities = new ArrayList<>();
        long[] carvingMask = null;
        if (chunk.getPersistedStatus().getChunkType() == ChunkType.PROTOCHUNK) {
            ProtoChunk protoChunk = (ProtoChunk)chunk;
            entities.addAll(protoChunk.getEntities());
            CarvingMask existingMask = protoChunk.getCarvingMask();
            if (existingMask != null) {
                carvingMask = existingMask.toArray();
            }
        }

        Map<Heightmap.Types, long[]> heightmaps = new EnumMap<>(Heightmap.Types.class);

        for (Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            if (chunk.getPersistedStatus().heightmapsAfter().contains(entry.getKey())) {
                long[] data = entry.getValue().getRawData();
                heightmaps.put(entry.getKey(), (long[])data.clone());
            }
        }

        ChunkAccess.PackedTicks ticksForSerialization = chunk.getTicksForSerialization(level.getGameTime());
        ShortList[] postProcessingSections = Arrays.stream(chunk.getPostProcessing())
                .map(shorts -> shorts != null && !shorts.isEmpty() ? new ShortArrayList(shorts) : null)
                .toArray(ShortList[]::new);
//        CompoundTag structureData = packStructureData(StructurePieceSerializationContext.fromLevel(level), pos, chunk.getAllStarts(), chunk.getAllReferences());
        CompoundTag structureData = new CompoundTag();
        return new SerializableChunkData(
                level.palettedContainerFactory(),
                pos,
                chunk.getMinSectionY(),
                level.getGameTime(),
                chunk.getInhabitedTime(),
                chunk.getPersistedStatus(),
                Optionull.map(chunk.getBlendingData(), BlendingData::pack),
                chunk.getBelowZeroRetrogen(),
                chunk.getUpgradeData().copy(),
                carvingMask,
                heightmaps,
                ticksForSerialization,
                postProcessingSections,
                chunk.isLightCorrect(),
                sectionData,
                entities,
                blockEntities,
                structureData
        );
    }
}
