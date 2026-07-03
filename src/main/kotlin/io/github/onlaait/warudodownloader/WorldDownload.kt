package io.github.onlaait.warudodownloader

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.stream.JsonWriter
import com.mojang.serialization.JsonOps
import com.mojang.serialization.Lifecycle
import io.github.onlaait.warudodownloader.gui.Minimap
import io.github.onlaait.warudodownloader.mixin.*
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.SharedConstants
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
import net.minecraft.core.Holder
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.NbtUtils
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.WorldLoader
import net.minecraft.server.WorldStem
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.pack.PackMetadataSection
import net.minecraft.server.packs.repository.PackRepository
import net.minecraft.server.packs.repository.ServerPacksSource
import net.minecraft.util.GsonHelper
import net.minecraft.util.ProblemReporter
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.entity.decoration.Mannequin
import net.minecraft.world.entity.player.Player
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.level.*
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.storage.IOWorker
import net.minecraft.world.level.chunk.storage.RegionStorageInfo
import net.minecraft.world.level.dimension.DimensionType
import net.minecraft.world.level.dimension.LevelStem
import net.minecraft.world.level.levelgen.FlatLevelSource
import net.minecraft.world.level.levelgen.WorldDimensions
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import net.minecraft.world.level.storage.*
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Team
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.io.path.*

object WorldDownload {

    private var current: Session? = null

    init {
        ClientTickEvents.END_WORLD_TICK.register { currentLevel ->
            onWorldTick(currentLevel)
        }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            onClientTick(mc)
        }
    }

    fun start(distance: Int) {
        require(!isStarted())
        current = Session(distance)
    }

    fun isStarted(): Boolean = current != null

    private fun onWorldTick(currentLevel: ClientLevel) {
        val wd = current ?: return
        if (currentLevel != wd.level) {
            stop()
            return
        }
        val player = wd.mc.player ?: return
        val chunkPos = player.chunkPosition()
        wd.minimap.playerX = chunkPos.x
        wd.minimap.playerY = chunkPos.z
        if (chunkPos == wd.lastChunkPos) return

        wd.saveAllInRange(chunkPos)
    }

    private fun onClientTick(mc: Minecraft) {
        val wd = current ?: return
        if (mc.level == null) {
            stop()
            return
        }
        if (++wd.ticksToSave == 1200) {
            wd.ticksToSave = 0
            wd.saveMapData()
        }
    }

    fun handleMapData(mapId: MapId, mapItemSavedData: MapItemSavedData) {
        val wd = current ?: return
        wd.setMapData(mapId, mapItemSavedData)
    }

    fun stop() {
        current!!.stop()
        current = null
    }

    private class Session(val distance: Int) {

        private companion object {
            const val DATAPACK_NAME = "warudodownloader"
            val GSON = GsonBuilder().setPrettyPrinting().create()

            fun <T : Any> writeData(dir: Path, resourceKey: ResourceKey<Registry<T>>, id: ResourceKey<*>, tag: JsonElement) {
                val idLocation = id.location()
                val dimensionTypeDir = dir.resolve(idLocation.namespace)
                    .resolve(resourceKey.location().path)
                    .resolve("${idLocation.path}.json")
                dimensionTypeDir.createParentDirectories()
                dimensionTypeDir.writer().use {
                    GSON.toJson(tag, it)
                }
            }

            fun GameRules.setGameRule(key: GameRules.Key<out GameRules.Value<*>>, value: Any) {
                val rule = getRule(key)
                when (value) {
                    is Boolean -> (rule as GameRules.BooleanValue).set(value, null)
                    is Int -> (rule as GameRules.IntegerValue).set(value, null)
                }
            }
        }

        val mc = Minecraft.getInstance()
        val level = mc.level!!
        var lastChunkPos: ChunkPos? = null
        val worldPathStr = "WD_" + (mc.currentServer?.ip?.replaceFirst(":", "") ?: "localworld")
        val chunkWorker: IOWorker
        val entitiesWorker: IOWorker
        val dimensionDataStorage: DimensionDataStorage

        var ticksToSave = 0
        var lastChunks = ArrayList<Long>(nMaxChunkInRange)
        var currentChunks = ArrayList<Long>(nMaxChunkInRange)
        val minimap = Minimap()
        val nMaxChunkInRange: Int
            get() = (distance * 2 + 1).let { it * it }

        init {
            val player = mc.player!!
            val levelPath = mc.levelSource.getLevelPath(worldPathStr)
            val regionPath = levelPath.resolve("region")
            val regionStorageInfo = RegionStorageInfo("WorldDownload", Level.OVERWORLD, "chunk")
            chunkWorker = IOWorkerAccessor.init(regionStorageInfo, regionPath, false)
            entitiesWorker = IOWorkerAccessor.init(RegionStorageInfo("WorldDownload", Level.OVERWORLD, "entities"), levelPath.resolve("entities"), false)

            val levelStorageAccess = mc.levelSource.createAccess(worldPathStr)

            val frozen = createWorld(levelStorageAccess)

            val dataFolder = levelStorageAccess.getDimensionPath(Level.OVERWORLD).resolve("data")
            levelStorageAccess.safeClose()
            dataFolder.createDirectories()
            val context = SavedData.Context(null, 0)
            dimensionDataStorage = DimensionDataStorage(context, dataFolder, mc.fixerUpper, frozen)

            mc.connection!!.serverData?.iconBytes?.let { icon ->
                levelPath.resolve("icon.png").writeBytes(icon)
            }

            for ((i, data) in ((Minecraft.getInstance().downloadedPackSource as DownloadedPackSourceAccessor).warudodownloader_getManager() as ServerPackManagerAccessor).warudodownloader_getPacks().withIndex()) {
                val path = (data as ServerPackManagerServerPackDataAccessor).warudodownloader_getPath() ?: continue
                val fileName =
                    if (i == 0) {
                        "resources.zip"
                    } else {
                        "resources$i.zip"
                    }
                path.copyTo(levelPath.resolve(fileName))
            }

            (level as ClientLevelAccessor).warudodownloader_getAllMapData().forEach { (mapId, data) ->
                setMapData(mapId, data)
            }
            saveMapData()

            val files = WorldUpgraderAbstractUpgraderAccessor.getAllChunkPositions(regionStorageInfo, regionPath)
            files.forEach { file ->
                file.chunksToUpgrade.forEach {
                    minimap.addPixel(it.x, it.z)
                }
            }
            minimap.init()

            player.displayClientMessage(Component.translatable("warudo-downloader.started", distance), false)
        }

        fun createWorld(levelStorageAccess: LevelStorageSource.LevelStorageAccess): RegistryAccess.Frozen {
            val packRepository: PackRepository
            val worldDataConfiguration: WorldDataConfiguration

            val nonVanillaDatas = getNonVanillaDatas()
            if (nonVanillaDatas.isEmpty()) {
                packRepository = PackRepository(ServerPacksSource(mc.directoryValidator()))
                worldDataConfiguration = WorldDataConfiguration.DEFAULT
            } else {
                val datapackDir = levelStorageAccess.getLevelPath(LevelResource.DATAPACK_DIR).resolve(DATAPACK_NAME)
                @OptIn(ExperimentalPathApi::class)
                datapackDir.deleteRecursively()
                datapackDir.createParentDirectories()
                val component = Component.literal("Downloaded data")

                // FROM net.minecraft.server.commands.DataPackCommand.createPack
                val path2 = datapackDir
                val packMetadataSection = PackMetadataSection(component, SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).minorRange())
                val dataResult = PackMetadataSection.SERVER_TYPE.codec().encodeStart(JsonOps.INSTANCE, packMetadataSection)
                val jsonObject = JsonObject()
                jsonObject.add(PackMetadataSection.SERVER_TYPE.name(), dataResult.getOrThrow())
                Files.createDirectory(path2)
                Files.createDirectory(path2.resolve(PackType.SERVER_DATA.directory))
                JsonWriter(path2.resolve("pack.mcmeta").bufferedWriter()).use { jsonWriter ->
                    jsonWriter.serializeNulls = false
                    jsonWriter.setIndent("  ")
                    GsonHelper.writeValue(jsonWriter, jsonObject, null)
                }

                val dataDir = path2.resolve("data")
                nonVanillaDatas.dimensionType?.let { dimensionType ->
                    writeData(dataDir, Registries.DIMENSION_TYPE, dimensionType.id, dimensionType.data)
                }
                nonVanillaDatas.biomes.forEach { biome ->
                    writeData(dataDir, Registries.BIOME, biome.id, biome.data)
                }
                val dataPackConfig = DataPackConfig(DataPackConfig.DEFAULT.enabled + "file/$DATAPACK_NAME", DataPackConfig.DEFAULT.disabled)
                worldDataConfiguration = WorldDataConfiguration(dataPackConfig, FeatureFlags.DEFAULT_FLAGS)
                packRepository = ServerPacksSource.createPackRepository(levelStorageAccess)
                packRepository.reload()
                packRepository.setSelected(worldDataConfiguration.dataPacks.enabled)
            }

            val gameRules = GameRules(worldDataConfiguration.enabledFeatures).apply {
                arrayOf(
                    GameRules.RULE_SPAWN_RADIUS to 0,
                    GameRules.RULE_MAX_ENTITY_CRAMMING to 0,
                    GameRules.RULE_MOBGRIEFING to false,
                    GameRules.RULE_DOMOBSPAWNING to false,
                    GameRules.RULE_DAYLIGHT to false,
                    GameRules.RULE_DOFIRETICK to false,
                    GameRules.RULE_WEATHER_CYCLE to false,
                    GameRules.RULE_RANDOMTICKING to 0,
                ).forEach { (k, v) ->
                    setGameRule(k, v)
                }
            }
            val initConfig = CreateWorldScreenAccessor.createDefaultLoadConfig(packRepository, worldDataConfiguration)
            val worldStem = Util.blockUntilDone { executor ->
                WorldLoader.load(
                    initConfig,
                    { dataLoadContext ->
                        val provider = dataLoadContext.datapackWorldgen
                        val dimensionType = provider
                            .lookupOrThrow(Registries.DIMENSION_TYPE)
                            .getOrThrow(level.dimensionTypeRegistration().unwrapKey().get())
                        val settings = provider
                            .lookupOrThrow(Registries.FLAT_LEVEL_GENERATOR_PRESET)
                            .getOrThrow(FlatLevelGeneratorPresets.THE_VOID)
                            .value()
                            .settings
                        val levelStem = LevelStem(dimensionType, FlatLevelSource(settings))
                        val registry = MappedRegistry(Registries.LEVEL_STEM, Lifecycle.stable()).freeze()
                        val complete = WorldDimensions(mapOf(LevelStem.OVERWORLD to levelStem))
                            .bake(registry)
                        val levelSettings = LevelSettings(
                            "Downloaded World: ${mc.currentServer?.ip ?: "localworld"}",
                            GameType.SPECTATOR,
                            false,
                            Difficulty.NORMAL,
                            true,
                            gameRules,
                            worldDataConfiguration
                        )
                        WorldLoader.DataLoadOutput(
                            PrimaryLevelData(
                                levelSettings,
                                WorldOptions(0L, false, false),
                                complete.specialWorldProperty(),
                                complete.lifecycle()
                            ), complete.dimensionsRegistryAccess()
                        )
                    },
                    { closeableResourceManager, reloadableServerResources, layeredRegistryAccess, worldData ->
                        WorldStem(
                            closeableResourceManager,
                            reloadableServerResources,
                            layeredRegistryAccess,
                            worldData
                        )
                    },
                    Util.backgroundExecutor(),
                    executor
                )
            }.get()
            val frozen = worldStem.registries.compositeAccess()
            val worldData = worldStem.worldData as PrimaryLevelData
            worldData.run {
                val clientLevelData = level.levelData
                setSpawn(clientLevelData.respawnData)
                dayTime = clientLevelData.dayTime
                isRaining = clientLevelData.isRaining
                isThundering = clientLevelData.isThundering
                isInitialized = true
                legacyWorldBorderSettings = Optional.of(WorldBorder.Settings(level.worldBorder))
            }
            levelStorageAccess.saveDataTag(frozen, worldData)
            return frozen
        }

        private fun getNonVanillaDatas(): NonVanillaDatas {
            val packRepository = PackRepository(ServerPacksSource(mc.directoryValidator()))
            val initConfig = CreateWorldScreenAccessor.createDefaultLoadConfig(packRepository, WorldDataConfiguration.DEFAULT)
            lateinit var vanillaDimensionTypes: List<Holder.Reference<DimensionType>>
            lateinit var vanillaBiomes: List<Holder.Reference<Biome>>
            Util.blockUntilDone { executor ->
                try {
                    WorldLoader.load<Any, Any>(
                        initConfig,
                        { dataLoadContext ->
                            val provider = dataLoadContext.datapackWorldgen
                            vanillaDimensionTypes = provider.lookupOrThrow(Registries.DIMENSION_TYPE).listElements().toList()
                            vanillaBiomes = provider.lookupOrThrow(Registries.BIOME).listElements().toList()
                            throw InterruptedException()
                        },
                        { _, _, _, _ -> 0 },
                        Util.backgroundExecutor(),
                        executor
                    )
                } catch (_: InterruptedException) {
                }
                CompletableFuture.completedFuture(0)
            }.get()
            val dynamicOps = level.registryAccess().createSerializationContext(NbtOps.INSTANCE)

            var nonVanillaDimensionType: NonVanillaDatas.DimensionType? = null
            WarudoDownloader.LOGGER.info("vanillaDimensionTypes: $vanillaDimensionTypes")
            val levelDimensionTypeHolder = level.dimensionTypeRegistration()
            WarudoDownloader.LOGGER.info("levelDimensionType: $levelDimensionTypeHolder")
            val levelDimensionTypeId = levelDimensionTypeHolder.unwrapKey().get()
            val levelDimensionType = level.dimensionType()
            val dimensionTypeCodec = DimensionType.DIRECT_CODEC
            WarudoDownloader.LOGGER.info("vanillaDimensionTypeTags: ${vanillaDimensionTypes.associate { it.key() to dimensionTypeCodec.encodeStart(dynamicOps, it.value()).getOrThrow() }}")
            val levelDimensionTypeTag = dimensionTypeCodec.encodeStart(dynamicOps, levelDimensionType).getOrThrow()
            WarudoDownloader.LOGGER.info("levelDimensionTypeTag: $levelDimensionTypeTag")
            if (vanillaDimensionTypes.any { it.key() == levelDimensionTypeId && dimensionTypeCodec.encodeStart(dynamicOps, it.value()).getOrThrow() == levelDimensionTypeTag }) {
                WarudoDownloader.LOGGER.info("levelDimensionType is vanilla")
            } else {
                WarudoDownloader.LOGGER.info("levelDimensionType is not vanilla")
                val json = dimensionTypeCodec.encodeStart(JsonOps.INSTANCE, levelDimensionType).getOrThrow()
                nonVanillaDimensionType = NonVanillaDatas.DimensionType(levelDimensionTypeId, json)
            }

            val nonVanillaBiomes = mutableListOf<NonVanillaDatas.Biome>()
            val levelBiomes = level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().toList()
            WarudoDownloader.LOGGER.info("vanillaBiomes: $vanillaBiomes")
            WarudoDownloader.LOGGER.info("levelBiomes: $levelBiomes")
            val biomeCodec = Biome.NETWORK_CODEC
            val biomeWriteCodec = Biome.DIRECT_CODEC
            val vanillaBiomeTags = vanillaBiomes.associate {
                it.key() to biomeCodec.encodeStart(dynamicOps, it.value()).getOrThrow()
            }
            for (r in levelBiomes) {
                val key = r.key()
                val value = r.value()
                val tag = biomeCodec.encodeStart(dynamicOps, value).getOrThrow()
                val vanillaTag = vanillaBiomeTags[key]
                if (tag == vanillaTag) continue
                WarudoDownloader.LOGGER.info("biome not matches: $key\nvanilla: $vanillaTag\nlevel: $tag")
                val json = biomeWriteCodec.encodeStart(JsonOps.INSTANCE, value).getOrThrow()
                nonVanillaBiomes += NonVanillaDatas.Biome(key, json)
            }
            WarudoDownloader.LOGGER.info("nonVanillaBiomes: ${nonVanillaBiomes.map { it.id }}")

            return NonVanillaDatas(nonVanillaDimensionType, nonVanillaBiomes)
        }

        data class NonVanillaDatas(
            val dimensionType: NonVanillaDatas.DimensionType?,
            val biomes: List<NonVanillaDatas.Biome>,
        ) {
            data class DimensionType(
                val id: ResourceKey<net.minecraft.world.level.dimension.DimensionType>,
                val data: JsonElement
            )
            data class Biome(
                val id: ResourceKey<net.minecraft.world.level.biome.Biome>,
                val data: JsonElement
            )

            fun isEmpty(): Boolean = dimensionType == null && biomes.isEmpty()
        }

        fun stop() {
            chunkWorker.close()
            entitiesWorker.close()
            dimensionDataStorage.close()
            minimap.dispose()

            val player = mc.player
            if (player == null) {
                WarudoDownloader.LOGGER.info("Stopped downloading the world")
            } else {
                player.displayClientMessage(Component.translatable("warudo-downloader.stopped"), false)
            }
        }

        fun saveAllInRange(chunkPos: ChunkPos) {
            val x = chunkPos.x
            val z = chunkPos.z
            for (x in (x - distance)..(x + distance)) {
                for (z in (z - distance)..(z + distance)) {
                    val chunk = level.chunkSource.getChunk(x, z, false) ?: continue
                    val l = chunk.pos.toLong()
                    currentChunks += l
                    if (!lastChunks.contains(l)) save(chunk)
                }
            }
            val empty = lastChunks.apply { clear() }
            lastChunks = currentChunks
            currentChunks = empty
            lastChunkPos = chunkPos
        }

        fun save(chunkAccess: ChunkAccess) {
//            WarudoDownloader.logger.info("Downloading chunk ${chunkAccess.pos}")
            saveChunk(chunkAccess)
            saveEntities(chunkAccess)
            chunkAccess.pos.run {
                currentChunks += toLong()
                minimap.addPixel(x, z)
            }
        }

        fun saveChunk(chunkAccess: ChunkAccess) {
            // FROM net.minecraft.server.level.ChunkMap.save
            val chunkPos = chunkAccess.pos
            val serializableChunkData = ClientSerializableChunkData.copyOf(level, chunkAccess)
            val completableFuture = CompletableFuture.supplyAsync(serializableChunkData::write, Util.backgroundExecutor())
            chunkWorker.store(chunkPos, completableFuture::join).handle { _, throwable ->
                if (throwable != null) {
                    WarudoDownloader.LOGGER.error("Failed to save chunk {},{}", chunkPos.x, chunkPos.z, throwable)
                }
                null
            }
        }

        fun saveEntities(chunkAccess: ChunkAccess) {
            val chunkPos = chunkAccess.pos
            val chunkEntities: List<Entity> = run {
                val list = mutableListOf<Entity>()
                level.entitiesForRendering().forEach { e ->
                    val e = injectEntity(e)
                    if (e !is Player && e.chunkPosition() == chunkPos) list += e
                }
                list
            }
            if (chunkEntities.isEmpty()) return

            // FROM net.minecraft.world.level.chunk.storage.EntityStorage.storeEntities
            val compoundTag: CompoundTag
            ProblemReporter.ScopedCollector(ChunkAccess.problemPath(chunkPos), WarudoDownloader.LOGGER).use { scopedCollector ->
                val listTag = ListTag()
                chunkEntities.forEach { entity ->
                    try {
                        val tagValueOutput = TagValueOutput.createWithContext(scopedCollector.forChild(entity.problemPath()), entity.registryAccess())
                        if (entity.save(tagValueOutput)) {
                            injectSaveData(entity, tagValueOutput)
                            val compoundTagx = tagValueOutput.buildResult()
                            listTag.add(compoundTagx)
                        }
                    } catch (e: Exception) {
                        val errorMsg = "Failed to save entity ${entity.type}:$entity"
                        WarudoDownloader.LOGGER.error(errorMsg, e)
                        Minecraft.getInstance().player?.displayClientMessage(Component.literal(errorMsg).withStyle(ChatFormatting.RED), false)
                    }
                }
                compoundTag = NbtUtils.addCurrentDataVersion(CompoundTag())
                compoundTag.put("Entities", listTag)
                compoundTag.store("Position", ChunkPos.CODEC, chunkPos)
                reportSaveFailureIfPresent(entitiesWorker.store(chunkPos, compoundTag), chunkPos)
            }
        }

        private fun reportSaveFailureIfPresent(completableFuture: CompletableFuture<*>, chunkPos: ChunkPos?) {
            completableFuture.exceptionally { throwable ->
                WarudoDownloader.LOGGER.error("Failed to store entity chunk {}", chunkPos, throwable)
                null
            }
        }

        private fun injectEntity(entity: Entity): Entity =
            when (entity) {
                is RemotePlayer -> Mannequin(EntityType.MANNEQUIN, level).apply {
                    val acc = this as MannequinAccessor
                    setPos(entity.position())
                    xRot = entity.xRot
                    yRot = entity.yRot
                    pose = entity.pose
                    customName = PlayerTeam.formatNameForTeam(entity.team, entity.name)
                    if (entity.team?.nameTagVisibility != Team.Visibility.NEVER) isCustomNameVisible = true
                    val belowName = entity.belowNameDisplay()
                    if (belowName == null) {
                        acc.warudodownloader_setHideDescription(true)
                    } else {
                        acc.warudodownloader_setDescription(belowName)
                    }
                    acc.warudodownloader_setProfile(ResolvableProfile.createResolved(entity.gameProfile))
                    mainArm = entity.mainArm
                    EquipmentSlot.entries.forEach {
                        val item = entity.getItemBySlot(it)
                        if (!item.isEmpty) setItemSlot(it, item)
                    }
                    attributes.apply(entity.attributes.pack())
                    if (entity.abilities.flying) isNoGravity = true
                }
                else -> entity
            }

        private fun injectSaveData(entity: Entity, valueOutput: ValueOutput) {
            when (entity) {
                is ItemFrame -> {
                    valueOutput.putBoolean("Fixed", true)
                }
                is ArmorStand -> {
                    if (entity.isInvisible && !entity.isMarker) valueOutput.putBoolean("NoGravity", true)
                }
            }
        }

        fun setMapData(mapId: MapId, mapItemSavedData: MapItemSavedData) {
            var mapItemSavedData = mapItemSavedData
            if (!mapItemSavedData.locked) mapItemSavedData = mapItemSavedData.locked()
            dimensionDataStorage.set(MapItemSavedData.type(mapId), mapItemSavedData)
        }

        fun saveMapData() {
            dimensionDataStorage.scheduleSave()
        }
    }
}