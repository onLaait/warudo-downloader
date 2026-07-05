package io.github.onlaait.warudodownloader;

import com.mojang.serialization.Codec;
import io.github.onlaait.warudodownloader.mixin.ChunkSerializerAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.Map;

import static io.github.onlaait.warudodownloader.mixin.ChunkSerializerAccessor.makeBiomeCodec;
import static net.minecraft.world.level.chunk.storage.ChunkSerializer.packOffsets;

public final class ClientChunkSerializer {

    public static CompoundTag write(ClientLevel clientLevel, ChunkAccess chunkAccess) {
        var serverLevel = clientLevel;
        var LOGGER = WarudoDownloader.INSTANCE.getLOGGER();
        var BLOCK_STATE_CODEC = ChunkSerializerAccessor.getBLOCK_STATE_CODEC();

        ChunkPos chunkPos = chunkAccess.getPos();
        CompoundTag compoundTag = NbtUtils.addCurrentDataVersion(new CompoundTag());
        compoundTag.putInt("xPos", chunkPos.x);
        compoundTag.putInt("yPos", chunkAccess.getMinSection());
        compoundTag.putInt("zPos", chunkPos.z);
        compoundTag.putLong("LastUpdate", serverLevel.getGameTime());
        compoundTag.putLong("InhabitedTime", chunkAccess.getInhabitedTime());
        compoundTag.putString("Status", BuiltInRegistries.CHUNK_STATUS.getKey(chunkAccess.getPersistedStatus()).toString());
        BlendingData blendingData = chunkAccess.getBlendingData();
        if (blendingData != null) {
            BlendingData.CODEC.encodeStart(NbtOps.INSTANCE, blendingData).resultOrPartial(LOGGER::error).ifPresent(tag -> compoundTag.put("blending_data", tag));
        }

        BelowZeroRetrogen belowZeroRetrogen = chunkAccess.getBelowZeroRetrogen();
        if (belowZeroRetrogen != null) {
            BelowZeroRetrogen.CODEC
                    .encodeStart(NbtOps.INSTANCE, belowZeroRetrogen)
                    .resultOrPartial(LOGGER::error)
                    .ifPresent(tag -> compoundTag.put("below_zero_retrogen", tag));
        }

        UpgradeData upgradeData = chunkAccess.getUpgradeData();
        if (!upgradeData.isEmpty()) {
            compoundTag.put("UpgradeData", upgradeData.write());
        }

        LevelChunkSection[] levelChunkSections = chunkAccess.getSections();
        ListTag listTag = new ListTag();
        LevelLightEngine levelLightEngine = serverLevel.getChunkSource().getLightEngine();
        Registry<Biome> registry = serverLevel.registryAccess().registryOrThrow(Registries.BIOME);
        Codec<PalettedContainerRO<Holder<Biome>>> codec = makeBiomeCodec(registry);
        boolean bl = chunkAccess.isLightCorrect();

        for (int i = levelLightEngine.getMinLightSection(); i < levelLightEngine.getMaxLightSection(); i++) {
            int j = chunkAccess.getSectionIndexFromSectionY(i);
            boolean bl2 = j >= 0 && j < levelChunkSections.length;
            DataLayer dataLayer = levelLightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(chunkPos, i));
            DataLayer dataLayer2 = levelLightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(chunkPos, i));
            if (bl2 || dataLayer != null || dataLayer2 != null) {
                CompoundTag compoundTag2 = new CompoundTag();
                if (bl2) {
                    LevelChunkSection levelChunkSection = levelChunkSections[j];
                    compoundTag2.put("block_states", BLOCK_STATE_CODEC.encodeStart(NbtOps.INSTANCE, levelChunkSection.getStates()).getOrThrow());
                    compoundTag2.put("biomes", codec.encodeStart(NbtOps.INSTANCE, levelChunkSection.getBiomes()).getOrThrow());
                }

                if (dataLayer != null && !dataLayer.isEmpty()) {
                    compoundTag2.putByteArray("BlockLight", dataLayer.getData());
                }

                if (dataLayer2 != null && !dataLayer2.isEmpty()) {
                    compoundTag2.putByteArray("SkyLight", dataLayer2.getData());
                }

                if (!compoundTag2.isEmpty()) {
                    compoundTag2.putByte("Y", (byte)i);
                    listTag.add(compoundTag2);
                }
            }
        }

        compoundTag.put("sections", listTag);
        if (bl) {
            compoundTag.putBoolean("isLightOn", true);
        }

        ListTag listTag2 = new ListTag();

        for (BlockPos blockPos : chunkAccess.getBlockEntitiesPos()) {
            CompoundTag compoundTag3 = chunkAccess.getBlockEntityNbtForSaving(blockPos, serverLevel.registryAccess());
            if (compoundTag3 != null) {
                listTag2.add(compoundTag3);
            }
        }

        compoundTag.put("block_entities", listTag2);
        if (chunkAccess.getPersistedStatus().getChunkType() == ChunkType.PROTOCHUNK) {
            ProtoChunk protoChunk = (ProtoChunk)chunkAccess;
            ListTag listTag3 = new ListTag();
            listTag3.addAll(protoChunk.getEntities());
            compoundTag.put("entities", listTag3);
            CompoundTag compoundTag3 = new CompoundTag();

            for (GenerationStep.Carving carving : GenerationStep.Carving.values()) {
                CarvingMask carvingMask = protoChunk.getCarvingMask(carving);
                if (carvingMask != null) {
                    compoundTag3.putLongArray(carving.toString(), carvingMask.toArray());
                }
            }

            compoundTag.put("CarvingMasks", compoundTag3);
        }

        saveTicks(serverLevel, compoundTag, chunkAccess.getTicksForSerialization());
        compoundTag.put("PostProcessing", packOffsets(chunkAccess.getPostProcessing()));
        CompoundTag compoundTag4 = new CompoundTag();

        for (Map.Entry<Heightmap.Types, Heightmap> entry : chunkAccess.getHeightmaps()) {
            if (chunkAccess.getPersistedStatus().heightmapsAfter().contains(entry.getKey())) {
                compoundTag4.put(entry.getKey().getSerializationKey(), new LongArrayTag(entry.getValue().getRawData()));
            }
        }

        compoundTag.put("Heightmaps", compoundTag4);
        compoundTag.put(
                "structures",
//                packStructureData(StructurePieceSerializationContext.fromLevel(serverLevel), chunkPos, chunkAccess.getAllStarts(), chunkAccess.getAllReferences())
                new CompoundTag()
        );
        return compoundTag;
    }

    private static void saveTicks(ClientLevel clientLevel, CompoundTag compoundTag, ChunkAccess.TicksToSave ticksToSave) {
        var serverLevel = clientLevel;

        long l = serverLevel.getLevelData().getGameTime();
        compoundTag.put("block_ticks", ticksToSave.blocks().save(l, block -> BuiltInRegistries.BLOCK.getKey(block).toString()));
        compoundTag.put("fluid_ticks", ticksToSave.fluids().save(l, fluid -> BuiltInRegistries.FLUID.getKey(fluid).toString()));
    }
}
