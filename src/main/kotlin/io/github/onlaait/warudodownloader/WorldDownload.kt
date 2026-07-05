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
import net.minecraft.server.packs.FilePackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.pack.PackMetadataSection
import net.minecraft.server.packs.repository.PackRepository
import net.minecraft.server.packs.repository.ServerPacksSource
import net.minecraft.util.GsonHelper
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.entity.player.Player
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.item.MapItem
import net.minecraft.world.level.*
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.storage.IOWorker
import net.minecraft.world.level.dimension.DimensionType
import net.minecraft.world.level.dimension.LevelStem
import net.minecraft.world.level.levelgen.FlatLevelSource
import net.minecraft.world.level.levelgen.WorldDimensions
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import net.minecraft.world.level.storage.DimensionDataStorage
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.level.storage.LevelStorageSource
import net.minecraft.world.level.storage.PrimaryLevelData
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.*

object WorldDownload {

    private var current: Session? = null

    init {
        ClientTickEvents.END_WORLD_TICK.register { level ->
            onWorldTick(level)
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

    private fun onWorldTick(level: ClientLevel) {
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
            wd.saveMapData()
        }
    }

    fun handleMapData(mapId: Int, mapItemSavedData: MapItemSavedData) {
        val wd = current ?: return
        val string = MapItem.makeKey(mapId)
        wd.setMapData(string, mapItemSavedData)
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
            val levelPath = (mc.levelSource as LevelStorageSourceAccessor).warudodownloader_getLevelPath(worldPathStr)
            val regionPath = levelPath.resolve("region")
            chunkWorker = IOWorkerAccessor.init(regionPath, false, "chunk")
            entitiesWorker = IOWorkerAccessor.init(levelPath.resolve("entities"), false, "entities")

            val levelStorageAccess = mc.levelSource.createAccess(worldPathStr)

            createWorld(levelStorageAccess)

            val dataFolder = levelStorageAccess.getDimensionPath(Level.OVERWORLD).resolve("data")
            levelStorageAccess.close()
            dataFolder.createDirectories()
            dimensionDataStorage = DimensionDataStorage(dataFolder.toFile(), mc.fixerUpper)

            mc.connection!!.serverData?.iconBytes?.let { icon ->
                levelPath.resolve("icon.png").writeBytes(icon)
            }

            (mc.downloadedPackSource as DownloadedPackSourceAccessor).warudodownloader_getServerPack()?.let { pack ->
                val packResources = pack.open() as FilePackResources
                val file = (packResources as FilePackResourcesAccessor).warudodownloader_getFile()
                file.copyTo(levelPath.resolve("resources.zip").toFile())
            }

            (level as ClientLevelAccessor).warudodownloader_getAllMapData().forEach { (mapId, data) ->
                setMapData(mapId, data)
            }
            saveMapData()

            val chunks = AbstractWorldUpgrader(levelStorageAccess).getAllChunkPos(Level.OVERWORLD)
            chunks.forEach {
                minimap.addPixel(it.x, it.z)
            }
            minimap.init()

            player.displayClientMessage(Component.translatable("warudo-downloader.started", distance), false)
        }

        fun createWorld(levelStorageAccess: LevelStorageSource.LevelStorageAccess) {
            val packRepository: PackRepository
            val worldDataConfiguration: WorldDataConfiguration

            val nonVanillaDatas = getNonVanillaDatas()
            if (nonVanillaDatas.isEmpty()) {
                packRepository = PackRepository(ServerPacksSource())
                worldDataConfiguration = WorldDataConfiguration.DEFAULT
            } else {
                val datapackDir = levelStorageAccess.getLevelPath(LevelResource.DATAPACK_DIR).resolve(DATAPACK_NAME)
                @OptIn(ExperimentalPathApi::class)
                datapackDir.deleteRecursively()
                datapackDir.createDirectories()

                val component = Component.literal("Downloaded data")
                val packMetadataSection = PackMetadataSection(
                    component, SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA)
                )
                val metadataSectionJson = PackMetadataSection.TYPE.toJson(packMetadataSection)
                val jsonObject = JsonObject()
                jsonObject.add(PackMetadataSection.TYPE.metadataSectionName, metadataSectionJson)
                JsonWriter(datapackDir.resolve("pack.mcmeta").bufferedWriter()).use { jsonWriter ->
                    jsonWriter.serializeNulls = false
                    jsonWriter.setIndent("  ")
                    GsonHelper.writeValue(jsonWriter, jsonObject, null)
                }

                val dataDir = datapackDir.resolve(PackType.SERVER_DATA.directory)
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

            val gameRules = GameRules().apply {
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
                        val dimensions = MappedRegistry(Registries.LEVEL_STEM, Lifecycle.experimental()).apply {
                            register(LevelStem.OVERWORLD, levelStem, Lifecycle.stable())
                        }.freeze()
                        val registry = MappedRegistry(Registries.LEVEL_STEM, Lifecycle.stable()).freeze()
                        val complete = WorldDimensions(dimensions)
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
                xSpawn = clientLevelData.xSpawn
                ySpawn = clientLevelData.ySpawn
                zSpawn = clientLevelData.zSpawn
                spawnAngle = clientLevelData.spawnAngle
                dayTime = clientLevelData.dayTime
                isRaining = clientLevelData.isRaining
                isThundering = clientLevelData.isThundering
                isInitialized = true
                worldBorder = WorldBorderSettingsAccessor.init(level.worldBorder)
            }
            levelStorageAccess.saveDataTag(frozen, worldData)
        }

        private fun getNonVanillaDatas(): NonVanillaDatas {
            val packRepository = PackRepository(ServerPacksSource())
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
            val dynamicOps = NbtOps.INSTANCE

            var nonVanillaDimensionType: NonVanillaDatas.DimensionType? = null
            WarudoDownloader.LOGGER.info("vanillaDimensionTypes: $vanillaDimensionTypes")
            val levelDimensionTypeHolder = level.dimensionTypeRegistration()
            WarudoDownloader.LOGGER.info("levelDimensionType: $levelDimensionTypeHolder")
            val levelDimensionTypeId = levelDimensionTypeHolder.unwrapKey().get()
            val levelDimensionType = level.dimensionType()
            val dimensionTypeCodec = DimensionType.DIRECT_CODEC
            WarudoDownloader.LOGGER.info("vanillaDimensionTypeTags: ${vanillaDimensionTypes.associate { it.key() to dimensionTypeCodec.encodeStart(dynamicOps, it.value()).getOrThrow(false) {} }}")
            val levelDimensionTypeTag = dimensionTypeCodec.encodeStart(dynamicOps, levelDimensionType).getOrThrow(false) {}
            WarudoDownloader.LOGGER.info("levelDimensionTypeTag: $levelDimensionTypeTag")
            if (vanillaDimensionTypes.any { it.key() == levelDimensionTypeId && dimensionTypeCodec.encodeStart(dynamicOps, it.value()).getOrThrow(false) {} == levelDimensionTypeTag }) {
                WarudoDownloader.LOGGER.info("levelDimensionType is vanilla")
            } else {
                WarudoDownloader.LOGGER.info("levelDimensionType is not vanilla")
                val json = dimensionTypeCodec.encodeStart(JsonOps.INSTANCE, levelDimensionType).getOrThrow(false) {}
                nonVanillaDimensionType = NonVanillaDatas.DimensionType(levelDimensionTypeId, json)
            }

            val nonVanillaBiomes = mutableListOf<NonVanillaDatas.Biome>()
            val levelBiomes = level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().toList()
            WarudoDownloader.LOGGER.info("vanillaBiomes: $vanillaBiomes")
            WarudoDownloader.LOGGER.info("levelBiomes: $levelBiomes")
            val biomeCodec = Biome.NETWORK_CODEC
            val biomeWriteCodec = ClientBiome.DIRECT_CODEC
            val vanillaBiomeTags = vanillaBiomes.associate {
                it.key() to biomeCodec.encodeStart(dynamicOps, it.value()).getOrThrow(false) {}
            }
            for (r in levelBiomes) {
                val key = r.key()
                val value = r.value()
                val tag = biomeCodec.encodeStart(dynamicOps, value).getOrThrow(false) {}
                val vanillaTag = vanillaBiomeTags[key]
                if (tag == vanillaTag) continue
                WarudoDownloader.LOGGER.info("biome not matches: $key\nvanilla: $vanillaTag\nlevel: $tag")
                val json = biomeWriteCodec.encodeStart(JsonOps.INSTANCE, value).getOrThrow(false) {}
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

        fun save(chunk: ChunkAccess) {
//            WarudoDownloader.logger.info("Downloading chunk ${chunk.pos}")
            saveChunk(chunk)
            saveEntities(chunk)
            chunk.pos.run {
                currentChunks += toLong()
                minimap.addPixel(x, z)
            }
        }

        fun saveChunk(chunk: ChunkAccess) {
            // FROM net.minecraft.server.level.ChunkMap.save
            val chunkPos = chunk.pos
            val compoundTag = ClientChunkSerializer.write(level, chunk)
            chunkWorker.store(chunkPos, compoundTag).exceptionally { throwable ->
                WarudoDownloader.LOGGER.error("Failed to save chunk {},{}", chunkPos.x, chunkPos.z, throwable)
                null
            }
        }

        fun saveEntities(chunk: ChunkAccess) {
            val chunkPos = chunk.pos
            val chunkEntities = level.entitiesForRendering().filter { it !is Player && it.chunkPosition() == chunkPos }
            if (chunkEntities.isEmpty()) return

            // FROM net.minecraft.world.level.chunk.storage.EntityStorage.storeEntities
            val listTag = ListTag()
            chunkEntities.forEach { entity ->
                try {
                    val compoundTagx = CompoundTag()
                    if (entity.save(compoundTagx)) {
                        injectSaveData(entity, compoundTagx)
                        listTag.add(compoundTagx)
                    }
                } catch (e: Exception) {
                    val errorMsg = "Failed to save entity ${entity.type}:$entity"
                    WarudoDownloader.LOGGER.error(errorMsg, e)
                    Minecraft.getInstance().player?.displayClientMessage(Component.literal(errorMsg).withStyle(ChatFormatting.RED), false)
                }
            }
            val compoundTag = NbtUtils.addCurrentDataVersion(CompoundTag())
            compoundTag.put("Entities", listTag)
            EntityStorageAccessor.writeChunkPos(compoundTag, chunkPos)
            entitiesWorker.store(chunkPos, compoundTag).exceptionally { throwable ->
                WarudoDownloader.LOGGER.error("Failed to store entity chunk {}", chunkPos, throwable)
                null
            }
        }

        private fun injectSaveData(entity: Entity, compoundTag: CompoundTag) {
            when (entity) {
                is ItemFrame -> {
                    compoundTag.putBoolean("Fixed", true)
                }
                is ArmorStand -> {
                    if (entity.isInvisible && !entity.isMarker) compoundTag.putBoolean("NoGravity", true)
                }
            }
        }

        fun setMapData(string: String, mapItemSavedData: MapItemSavedData) {
            val mapItemSavedData = if (mapItemSavedData.locked) mapItemSavedData else mapItemSavedData.locked()
            dimensionDataStorage.set(string, mapItemSavedData)
        }

        fun saveMapData() {
            dimensionDataStorage.save()
        }
    }
}