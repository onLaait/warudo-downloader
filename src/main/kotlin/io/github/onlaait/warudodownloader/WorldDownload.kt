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
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
import net.minecraft.core.Holder
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
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
import net.minecraft.util.Util
import net.minecraft.world.clock.ClockState
import net.minecraft.world.clock.PackedClockStates
import net.minecraft.world.clock.ServerClockManager
import net.minecraft.world.clock.WorldClocks
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes
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
import net.minecraft.world.level.gamerules.GameRule
import net.minecraft.world.level.gamerules.GameRuleMap
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.level.levelgen.FlatLevelSource
import net.minecraft.world.level.levelgen.WorldDimensions
import net.minecraft.world.level.levelgen.WorldGenSettings
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets
import net.minecraft.world.level.saveddata.WeatherData
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import net.minecraft.world.level.storage.*
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Team
import net.minecraft.world.timeline.Timeline
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.*

object WorldDownload {

    private var current: Session? = null

    init {
        ClientTickEvents.END_LEVEL_TICK.register { level ->
            onLevelTick(level)
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

    private fun onLevelTick(level: ClientLevel) {
        val wd = current ?: return
        if (level != wd.level) {
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
            wd.saveDataStorage()
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
                val idLocation = id.identifier()
                val dimensionTypeDir = dir.resolve(idLocation.namespace)
                    .resolve(resourceKey.identifier().path)
                    .resolve("${idLocation.path}.json")
                dimensionTypeDir.createParentDirectories()
                dimensionTypeDir.writer().use {
                    GSON.toJson(tag, it)
                }
            }

            fun <T : Any> GameRules.setGameRule(key: GameRule<T>, value: Any) {
                when (value) {
                    is Boolean -> set(key as GameRule<Boolean>, value, null)
                    is Int -> set(key as GameRule<Int>, value, null)
                }
            }
        }

        val mc = Minecraft.getInstance()
        val level = mc.level!!
        var lastChunkPos: ChunkPos? = null
        val worldPathStr = "WD_" + (mc.currentServer?.ip?.replaceFirst(":", "") ?: "localworld")
        val chunkWorker: IOWorker
        val entitiesWorker: IOWorker
        lateinit var commonDataStorage: SavedDataStorage

        var ticksToSave = 0
        var lastChunks = ArrayList<Long>(nMaxChunkInRange)
        var currentChunks = ArrayList<Long>(nMaxChunkInRange)
        val minimap = Minimap()
        val nMaxChunkInRange: Int
            get() = (distance * 2 + 1).let { it * it }

        init {
            val player = mc.player!!
            val levelStorage = mc.levelSource.createAccess(worldPathStr)
            val levelPath = levelStorage.levelDirectory.path

            val chunkStorageInfo = RegionStorageInfo(levelStorage.levelId, Level.OVERWORLD, "chunk")
            val regionPath = levelStorage.getDimensionPath(Level.OVERWORLD).resolve("region")
            chunkWorker = IOWorkerAccessor.init(chunkStorageInfo, regionPath, false)

            val entitiesStorageInfo = RegionStorageInfo(levelStorage.levelId, Level.OVERWORLD, "entities")
            val entitiesPath = levelStorage.getDimensionPath(Level.OVERWORLD).resolve("entities")
            entitiesWorker = IOWorkerAccessor.init(entitiesStorageInfo, entitiesPath, false)

            createWorld(levelStorage)

            (level as ClientLevelAccessor).warudodownloader_getAllMapData().forEach { (mapId, data) ->
                setMapData(mapId, data)
            }

            saveDataStorage()

            mc.connection!!.serverData?.iconBytes?.let { icon ->
                levelStorage.getLevelPath(LevelResource.ICON_FILE).writeBytes(icon)
            }

            val resourcepacksPath = levelPath.resolve("resourcepacks")
            for ((i, data) in ((Minecraft.getInstance().downloadedPackSource as DownloadedPackSourceAccessor).warudodownloader_getManager() as ServerPackManagerAccessor).warudodownloader_getPacks().withIndex()) {
                val path = (data as ServerPackManagerServerPackDataAccessor).warudodownloader_getPath() ?: continue
                val fileName =
                    if (i == 0) {
                        "resources.zip"
                    } else {
                        "resources$i.zip"
                    }
                path.copyTo(resourcepacksPath.resolve(fileName))
            }

            levelStorage.safeClose()

            val regionFileChunks = RegionStorageUpgraderAccessor.getAllChunkPositions(chunkStorageInfo, regionPath)
            regionFileChunks.forEach { regionFileChunk ->
                regionFileChunk.chunksToUpgrade.forEach {
                    minimap.addPixel(it.x, it.z)
                }
            }
            minimap.init()

            player.sendSystemMessage(Component.translatable("warudo-downloader.started", distance))
        }

        fun createWorld(levelStorage: LevelStorageSource.LevelStorageAccess) {
            val packRepository: PackRepository
            val worldDataConfiguration: WorldDataConfiguration

            val nonVanillaDatas = getNonVanillaDatas()
            if (nonVanillaDatas.isEmpty()) {
                packRepository = PackRepository(ServerPacksSource(mc.directoryValidator()))
                worldDataConfiguration = WorldDataConfiguration.DEFAULT
            } else {
                val datapackDir = levelStorage.getLevelPath(LevelResource.DATAPACK_DIR)
                val packDir = datapackDir.resolve(DATAPACK_NAME)
                @OptIn(ExperimentalPathApi::class)
                packDir.deleteRecursively()
                packDir.createDirectories()
                val description = Component.literal("Downloaded data")

                // FROM net.minecraft.server.commands.DataPackCommand.createPack
                val packMetadataSection = PackMetadataSection(
                    description, SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).minorRange()
                )
                val encodedMeta = PackMetadataSection.SERVER_TYPE.codec().encodeStart(JsonOps.INSTANCE, packMetadataSection)

                val topMcmeta = JsonObject()
                topMcmeta.add(PackMetadataSection.SERVER_TYPE.name(), encodedMeta.getOrThrow())

                Files.newBufferedWriter(packDir.resolve("pack.mcmeta"), StandardCharsets.UTF_8).use { mcmetaFile ->
                    JsonWriter(mcmetaFile).use { jsonWriter ->
                        jsonWriter.serializeNulls = false
                        jsonWriter.setIndent("  ")
                        GsonHelper.writeValue(jsonWriter, topMcmeta, null)
                    }
                }

                val dataDir = packDir.resolve(PackType.SERVER_DATA.directory)
                nonVanillaDatas.dimensionType?.let { dimensionType ->
                    writeData(dataDir, Registries.DIMENSION_TYPE, dimensionType.id, dimensionType.data)
                }
                nonVanillaDatas.biomes.forEach { biome ->
                    writeData(dataDir, Registries.BIOME, biome.id, biome.data)
                }
                nonVanillaDatas.timelines.forEach { timeline ->
                    writeData(dataDir, Registries.TIMELINE, timeline.id, timeline.data)
                }
                val dataPackConfig = DataPackConfig(DataPackConfig.DEFAULT.enabled + "file/$DATAPACK_NAME", DataPackConfig.DEFAULT.disabled)
                worldDataConfiguration = WorldDataConfiguration(dataPackConfig, FeatureFlags.DEFAULT_FLAGS)
                packRepository = ServerPacksSource.createPackRepository(levelStorage)
                packRepository.reload()
                packRepository.setSelected(worldDataConfiguration.dataPacks.enabled)
            }

            val initConfig = CreateWorldScreenAccessor.createDefaultLoadConfig(packRepository, worldDataConfiguration)
            val worldStem = Util.blockUntilDone { executor ->
                lateinit var dimensions: WorldDimensions
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
                        dimensions = WorldDimensions(mapOf(LevelStem.OVERWORLD to levelStem))
                        val complete = dimensions.bake(registry)
                        val levelSettings = LevelSettings(
                            "Downloaded World: ${mc.currentServer?.ip ?: "localworld"}",
                            GameType.SPECTATOR,
                            LevelSettings.DifficultySettings.DEFAULT,
                            true,
                            worldDataConfiguration
                        )
                        WorldLoader.DataLoadOutput(
                            PrimaryLevelData(
                                levelSettings,
                                complete.specialWorldProperty(),
                                complete.lifecycle()
                            ), complete.dimensionsRegistryAccess()
                        )
                    },
                    { closeableResourceManager, reloadableServerResources, layeredRegistryAccess, worldData ->
                        val worldOption = WorldOptions(0L, false, false)
                        val genSettings = WorldGenSettings(worldOption, dimensions)
                        val worldDataAndGenSettings = LevelDataAndDimensions.WorldDataAndGenSettings(worldData, genSettings)
                        WorldStem(
                            closeableResourceManager,
                            reloadableServerResources,
                            layeredRegistryAccess,
                            worldDataAndGenSettings
                        )
                    },
                    Util.backgroundExecutor(),
                    executor
                )
            }.get()
            val frozen = worldStem.registries.compositeAccess()
            val worldData = worldStem.worldDataAndGenSettings.data as PrimaryLevelData
            worldData.run {
                val clientLevelData = level.levelData
                setSpawn(clientLevelData.respawnData)
                isInitialized = true
            }
            levelStorage.saveDataTag(worldData, null)

            val commonDataFolder = levelStorage.getLevelPath(LevelResource.DATA)
            commonDataStorage = SavedDataStorage(commonDataFolder, mc.fixerUpper, frozen)

            commonDataStorage.set(WorldGenSettings.TYPE, worldStem.worldDataAndGenSettings.genSettings)

            val gameRules = GameRules(worldDataConfiguration.enabledFeatures).apply {
                arrayOf(
                    GameRules.RESPAWN_RADIUS to 0,
                    GameRules.MAX_ENTITY_CRAMMING to 0,
                    GameRules.MOB_GRIEFING to false,
                    GameRules.SPAWN_MOBS to false,
                    GameRules.ADVANCE_TIME to false,
                    GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER to 0,
                    GameRules.ADVANCE_WEATHER to false,
                    GameRules.RANDOM_TICK_SPEED to 0,
                ).forEach { (k, v) ->
                    setGameRule(k, v)
                }
            }
            val gameRuleMap = (gameRules as GameRulesAccessor).warudodownloader_getRules()
            commonDataStorage.set(GameRuleMap.TYPE, gameRuleMap)

            val weatherData = WeatherData(0, 0, 0, level.isRaining, level.isThundering)
            commonDataStorage.set(WeatherData.TYPE, weatherData)

            level.dimensionType().defaultClock.or { level.registryAccess().get(WorldClocks.OVERWORLD) }.ifPresent { clock ->
                val clockManager = level.clockManager()
                val serverClockManager = ServerClockManagerAccessor.init(PackedClockStates.EMPTY)
                val serverClocks = (serverClockManager as ServerClockManagerAccessor).warudodownloader_getClocks()
                val clockInstance = (clockManager as ClientClockManagerAccessor).warudodownloader_getInstance(clock)
                val clockState = (clockInstance as ClientClockManagerClockInstanceAccessor).run {
                    ClockState(warudodownloader_getTotalTicks(), warudodownloader_getPartialTick(), 1f, false)
                }
                val serverClockInstance = ServerClockManagerClockInstanceAccessor.init().apply {
                    loadFrom(clockState)
                }
                val overworldClock = frozen.lookupOrThrow(Registries.WORLD_CLOCK)
                    .getOrThrow(WorldClocks.OVERWORLD)
                serverClocks[overworldClock] = serverClockInstance
                commonDataStorage.set(ServerClockManager.TYPE, serverClockManager)
            }

            val overworldDataFolder = levelStorage.getDimensionPath(Level.OVERWORLD).resolve("data")
            SavedDataStorage(overworldDataFolder, mc.fixerUpper, frozen).use { overworldDataStorage ->
                overworldDataStorage.set(WorldBorder.TYPE, level.worldBorder)
                overworldDataStorage.saveAndJoin()
            }
        }

        private fun getNonVanillaDatas(): NonVanillaDatas {
            val packRepository = PackRepository(ServerPacksSource(mc.directoryValidator()))
            val initConfig = CreateWorldScreenAccessor.createDefaultLoadConfig(packRepository, WorldDataConfiguration.DEFAULT)
            lateinit var vanillaDimensionTypes: List<Holder.Reference<DimensionType>>
            lateinit var vanillaBiomes: List<Holder.Reference<Biome>>
            lateinit var vanillaTimelines: List<Holder.Reference<Timeline>>
            try {
                val load = WorldLoader.load<Any, Any>(
                    initConfig,
                    { dataLoadContext ->
                        val provider = dataLoadContext.datapackWorldgen
                        vanillaDimensionTypes = provider.lookupOrThrow(Registries.DIMENSION_TYPE).listElements().toList()
                        vanillaBiomes = provider.lookupOrThrow(Registries.BIOME).listElements().toList()
                        vanillaTimelines = provider.lookupOrThrow(Registries.TIMELINE).listElements().toList()
                        throw InterruptedException()
                    },
                    { _, _, _, _ -> 0 },
                    Util.backgroundExecutor(),
                    mc
                )
                mc.managedBlock(load::isDone)
            } catch (_: InterruptedException) {
            }
            val dynamicOps = level.registryAccess().createSerializationContext(NbtOps.INSTANCE)

            // DimensionType
            var nonVanillaDimensionType: NonVanillaDatas.DimensionType? = null
            WarudoDownloader.LOGGER.info("vanillaDimensionTypes: $vanillaDimensionTypes")
            val levelDimensionTypeHolder = level.dimensionTypeRegistration()
            WarudoDownloader.LOGGER.info("levelDimensionType: $levelDimensionTypeHolder")
            val levelDimensionTypeId = levelDimensionTypeHolder.unwrapKey().get()
            val levelDimensionType = level.dimensionType()
            val dimensionTypeCodec = ClientDimensionType.DIRECT_CODEC
            val levelDimensionTypeTag = dimensionTypeCodec.encodeStart(dynamicOps, levelDimensionType).getOrThrow()
            if (vanillaDimensionTypes.any { it.key() == levelDimensionTypeId && dimensionTypeCodec.encodeStart(dynamicOps, it.value()).getOrThrow() == levelDimensionTypeTag }) {
                WarudoDownloader.LOGGER.info("levelDimensionType is vanilla")
            } else {
                WarudoDownloader.LOGGER.info("levelDimensionType is not vanilla")
                val json = dimensionTypeCodec.encodeStart(JsonOps.INSTANCE, levelDimensionType).getOrThrow()
                nonVanillaDimensionType = NonVanillaDatas.DimensionType(levelDimensionTypeId, json)
            }

            // Biome
            val nonVanillaBiomes = mutableListOf<NonVanillaDatas.Biome>()
            val levelBiomes = level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().toList()
            WarudoDownloader.LOGGER.info("vanillaBiomes: $vanillaBiomes")
            WarudoDownloader.LOGGER.info("levelBiomes: $levelBiomes")
            val biomeCodec = ClientBiome.DIRECT_CODEC
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
                val json = biomeCodec.encodeStart(JsonOps.INSTANCE, value).getOrThrow()
                nonVanillaBiomes += NonVanillaDatas.Biome(key, json)
            }
            WarudoDownloader.LOGGER.info("nonVanillaBiomes: ${nonVanillaBiomes.map { it.id }}")

            // Timeline
            val nonVanillaTimelines = mutableListOf<NonVanillaDatas.Timeline>()
            WarudoDownloader.LOGGER.info("vanillaTimelines: $vanillaTimelines")
            val levelTimelines = levelDimensionType.timelines
            WarudoDownloader.LOGGER.info("levelTimelines: ${levelTimelines.toList()}")
            val timelineCodec = ClientTimeline.DIRECT_CODEC
            val vanillaTimelineTags = vanillaTimelines.associate {
                it.key() to timelineCodec.encodeStart(dynamicOps, it.value()).getOrThrow()
            }
            for (h in levelTimelines) {
                val key = h.unwrapKey().get()
                val value = h.value()
                val tag = timelineCodec.encodeStart(dynamicOps, value).getOrThrow()
                val vanillaTag = vanillaTimelineTags[key]
                if (tag == vanillaTag) continue
                WarudoDownloader.LOGGER.info("timeline not matches: $key\nvanilla: $vanillaTag\nlevel: $tag")
                val json = timelineCodec.encodeStart(JsonOps.INSTANCE, value).getOrThrow()
                nonVanillaTimelines += NonVanillaDatas.Timeline(key, json)
            }

            return NonVanillaDatas(nonVanillaDimensionType, nonVanillaBiomes, nonVanillaTimelines)
        }

        data class NonVanillaDatas(
            val dimensionType: NonVanillaDatas.DimensionType?,
            val biomes: List<NonVanillaDatas.Biome>,
            val timelines: List<NonVanillaDatas.Timeline>,
        ) {
            data class DimensionType(
                val id: ResourceKey<net.minecraft.world.level.dimension.DimensionType>,
                val data: JsonElement
            )
            data class Biome(
                val id: ResourceKey<net.minecraft.world.level.biome.Biome>,
                val data: JsonElement
            )
            data class Timeline(
                val id: ResourceKey<net.minecraft.world.timeline.Timeline>,
                val data: JsonElement
            )

            fun isEmpty(): Boolean = dimensionType == null && biomes.isEmpty() && timelines.isEmpty()
        }

        fun stop() {
            chunkWorker.close()
            entitiesWorker.close()
            commonDataStorage.close()
            minimap.dispose()

            val player = mc.player
            if (player == null) {
                WarudoDownloader.LOGGER.info("Stopped downloading the world")
            } else {
                player.sendSystemMessage(Component.translatable("warudo-downloader.stopped"))
            }
        }

        fun saveAllInRange(chunkPos: ChunkPos) {
            val x = chunkPos.x
            val z = chunkPos.z
            for (x in (x - distance)..(x + distance)) {
                for (z in (z - distance)..(z + distance)) {
                    val chunk = level.chunkSource.getChunk(x, z, false) ?: continue
                    val l = chunk.pos.pack()
                    currentChunks += l
                    if (!lastChunks.contains(l)) save(chunk)
                }
            }
            val empty = lastChunks.apply { clear() }
            lastChunks = currentChunks
            currentChunks = empty
            lastChunkPos = chunkPos
        }

        fun save(chunk: ChunkAccess) {
//            WarudoDownloader.LOGGER.info("Downloading chunk ${chunk.pos}")
            saveChunk(chunk)
            saveEntities(chunk)
            chunk.pos.run {
                currentChunks += pack()
                minimap.addPixel(x, z)
            }
        }

        fun saveChunk(chunk: ChunkAccess) {
            // FROM net.minecraft.server.level.ChunkMap.save
            val pos = chunk.pos
            val data = ClientSerializableChunkData.copyOf(level, chunk)
            val encodedData = CompletableFuture.supplyAsync(data::write, Util.backgroundExecutor())

            chunkWorker.store(pos, encodedData::join).handle { _, throwable ->
                if (throwable != null) {
                    WarudoDownloader.LOGGER.error("Failed to save chunk {},{}", pos.x, pos.z, throwable)
                }
                null
            }
        }

        fun saveEntities(chunk: ChunkAccess) {
            val pos = chunk.pos
            val chunkEntities: List<Entity> = run {
                val list = mutableListOf<Entity>()
                level.entitiesForRendering().forEach { e ->
                    val e = injectEntity(e)
                    if (e !is Player && e.chunkPosition() == pos) list += e
                }
                list
            }
            if (chunkEntities.isEmpty()) return

            // FROM net.minecraft.world.level.chunk.storage.EntityStorage.storeEntities
            val chunkTag: CompoundTag
            ProblemReporter.ScopedCollector(ChunkAccess.problemPath(pos), WarudoDownloader.LOGGER).use { reporter ->
                val entities = ListTag()
                chunkEntities.forEach { e ->
                    try {
                        val output = TagValueOutput.createWithContext(reporter.forChild(e.problemPath()), e.registryAccess())
                        if (e.save(output)) {
                            injectSaveData(e, output)
                            val result = output.buildResult()
                            entities.add(result)
                        }
                    } catch (ex: Exception) {
                        val errorMsg = "Failed to save entity ${e.type}:$e"
                        WarudoDownloader.LOGGER.error(errorMsg, ex)
                        Minecraft.getInstance().player?.sendSystemMessage(Component.literal(errorMsg).withStyle(ChatFormatting.RED))
                    }
                }
                chunkTag = NbtUtils.addCurrentDataVersion(CompoundTag())
                chunkTag.put("Entities", entities)
                chunkTag.store("Position", ChunkPos.CODEC, pos)
                reportSaveFailureIfPresent(entitiesWorker.store(pos, chunkTag), pos)
            }
        }

        private fun reportSaveFailureIfPresent(operation: CompletableFuture<*>, pos: ChunkPos) {
            operation.exceptionally { t ->
                WarudoDownloader.LOGGER.error("Failed to store entity chunk {}", pos, t)
                null
            }
        }

        private fun injectEntity(entity: Entity): Entity =
            when (entity) {
                is RemotePlayer -> Mannequin(EntityTypes.MANNEQUIN, level).apply {
                    val acc = this as MannequinAccessor
                    id = -1
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
            commonDataStorage.set(MapItemSavedData.type(mapId), mapItemSavedData)
        }

        fun saveDataStorage() {
            commonDataStorage.scheduleSave()
        }
    }
}