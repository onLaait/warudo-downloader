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
    public static SerializableChunkData copyOf(ClientLevel clientLevel, ChunkAccess chunkAccess) {
        var serverLevel = clientLevel;

        if (!chunkAccess.canBeSerialized()) {
            throw new IllegalArgumentException("Chunk can't be serialized: " + chunkAccess);
        }

        ChunkPos chunkPos = chunkAccess.getPos();
        List<SerializableChunkData.SectionData> list = new ArrayList<>();
        LevelChunkSection[] levelChunkSections = chunkAccess.getSections();
        LevelLightEngine levelLightEngine = serverLevel.getChunkSource().getLightEngine();

        for (int i = levelLightEngine.getMinLightSection(); i < levelLightEngine.getMaxLightSection(); i++) {
            int j = chunkAccess.getSectionIndexFromSectionY(i);
            boolean bl = j >= 0 && j < levelChunkSections.length;
            DataLayer dataLayer = levelLightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(chunkPos, i));
            DataLayer dataLayer2 = levelLightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(chunkPos, i));
            DataLayer dataLayer3 = dataLayer != null && !dataLayer.isEmpty() ? dataLayer.copy() : null;
            DataLayer dataLayer4 = dataLayer2 != null && !dataLayer2.isEmpty() ? dataLayer2.copy() : null;
            if (bl || dataLayer3 != null || dataLayer4 != null) {
                LevelChunkSection levelChunkSection = bl ? levelChunkSections[j].copy() : null;
                list.add(new SerializableChunkData.SectionData(i, levelChunkSection, dataLayer3, dataLayer4));
            }
        }

        List<CompoundTag> list2 = new ArrayList<>(chunkAccess.getBlockEntitiesPos().size());

        for (BlockPos blockPos : chunkAccess.getBlockEntitiesPos()) {
            CompoundTag compoundTag = chunkAccess.getBlockEntityNbtForSaving(blockPos, serverLevel.registryAccess());
            if (compoundTag != null) {
                list2.add(compoundTag);
            }
        }

        List<CompoundTag> list3 = new ArrayList<>();
        long[] ls = null;
        if (chunkAccess.getPersistedStatus().getChunkType() == ChunkType.PROTOCHUNK) {
            ProtoChunk protoChunk = (ProtoChunk)chunkAccess;
            list3.addAll(protoChunk.getEntities());
            CarvingMask carvingMask = protoChunk.getCarvingMask();
            if (carvingMask != null) {
                ls = carvingMask.toArray();
            }
        }

        Map<Heightmap.Types, long[]> map = new EnumMap<>(Heightmap.Types.class);

        for (Map.Entry<Heightmap.Types, Heightmap> entry : chunkAccess.getHeightmaps()) {
            if (chunkAccess.getPersistedStatus().heightmapsAfter().contains(entry.getKey())) {
                long[] ms = entry.getValue().getRawData();
                map.put(entry.getKey(), (long[])ms.clone());
            }
        }

        ChunkAccess.PackedTicks packedTicks = chunkAccess.getTicksForSerialization(serverLevel.getGameTime());
        ShortList[] shortLists = Arrays.stream(chunkAccess.getPostProcessing())
                .map(shortList -> shortList != null ? new ShortArrayList(shortList) : null)
                .toArray(ShortList[]::new);
/*        CompoundTag compoundTag2 = packStructureData(
                StructurePieceSerializationContext.fromLevel(serverLevel), chunkPos, chunkAccess.getAllStarts(), chunkAccess.getAllReferences()
        );*/
        CompoundTag compoundTag2 = new CompoundTag();
        return new SerializableChunkData(
                serverLevel.palettedContainerFactory(),
                chunkPos,
                chunkAccess.getMinSectionY(),
                serverLevel.getGameTime(),
                chunkAccess.getInhabitedTime(),
                chunkAccess.getPersistedStatus(),
                Optionull.map(chunkAccess.getBlendingData(), BlendingData::pack),
                chunkAccess.getBelowZeroRetrogen(),
                chunkAccess.getUpgradeData().copy(),
                ls,
                map,
                packedTicks,
                shortLists,
                chunkAccess.isLightCorrect(),
                list,
                list3,
                list2,
                compoundTag2
        );
    }
}
