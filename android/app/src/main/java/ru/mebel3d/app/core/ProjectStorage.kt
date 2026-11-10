package ru.mebel3d.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Compact entry shown in the on-device project manager. */
data class StoredProjectSummary(
    val id: String,
    val name: String,
    val roomCount: Int,
    val furnitureCount: Int,
    val isLastOpened: Boolean
)

/** Small local persistence layer. Schema is versioned for future migrations. */
class ProjectStorage(private val context: Context) {
    private val preferences = context.getSharedPreferences("mebel3d_projects", Context.MODE_PRIVATE)

    /** Serialize only after validating the complete project; shared by save and atomic variant writes. */
    private fun encodeProject(project: ProjectModel): String {
        ProjectValidator.requireValid(project)
        val root = JSONObject().apply {
            put("schemaVersion", 14)
            put("id", project.id)
            put("name", project.name)
            put("activeRoomId", project.activeRoomId)
            put("rooms", JSONArray().apply {
                project.rooms.forEach { room -> put(JSONObject().apply {
                    put("id", room.id); put("name", room.name)
                    put("widthMm", room.widthMm); put("depthMm", room.depthMm)
                    put("heightMm", room.heightMm); put("wallThicknessMm", room.wallThicknessMm)
                    put("wallColorArgb", room.wallColorArgb); put("floorColorArgb", room.floorColorArgb); put("lightingPreset", room.lightingPreset)
                    put("wallMaterialId", room.wallMaterialId); put("floorMaterialId", room.floorMaterialId)
                    put("ceilingColorArgb", room.ceilingColorArgb); put("ceilingMaterialId", room.ceilingMaterialId)
                    put("northWallMaterialId", room.northWallMaterialId); put("eastWallMaterialId", room.eastWallMaterialId)
                    put("southWallMaterialId", room.southWallMaterialId); put("westWallMaterialId", room.westWallMaterialId)
                    put("lightIntensityPercent", room.lightIntensityPercent); put("lightTemperatureKelvin", room.lightTemperatureKelvin); put("shadowStrengthPercent", room.shadowStrengthPercent)
                    put("openings", JSONArray().apply { room.openings.forEach { o -> put(JSONObject().apply {
                        put("id", o.id); put("side", o.side.name); put("offsetMm", o.offsetMm)
                        put("widthMm", o.widthMm); put("sillHeightMm", o.sillHeightMm)
                        put("heightMm", o.heightMm); put("kind", o.kind.name)
                        put("sourcePhotoId", o.sourcePhotoId); put("sourceAnnotationId", o.sourceAnnotationId)
                    }) } })
                    put("engineering", JSONArray().apply { room.engineering.forEach { e -> put(JSONObject().apply {
                        put("id", e.id); put("name", e.name); put("kind", e.kind.name)
                        put("xMm", e.position.x); put("yMm", e.position.y); put("zMm", e.position.z)
                        put("wallSide", e.wallSide?.name); put("diameterMm", e.diameterMm); put("notes", e.notes)
                        put("sourcePhotoId", e.sourcePhotoId); put("sourceAnnotationId", e.sourceAnnotationId)
                    }) } })
                    put("photos", JSONArray().apply { room.photos.forEach { photo -> put(JSONObject().apply {
                        put("id", photo.id); put("uri", photo.uri); put("wallSide", photo.wallSide?.name)
                        put("knownLengthMm", photo.knownLengthMm); put("calibrationPixels", photo.calibrationPixels)
                        put("imageWidthPx", photo.imageWidthPx); put("imageHeightPx", photo.imageHeightPx)
                        put("wallOriginXPx", photo.wallOriginXPx); put("floorLineYPx", photo.floorLineYPx)
                        put("annotations", JSONArray().apply { photo.annotations.forEach { a -> put(JSONObject().apply {
                            put("id", a.id); put("kind", a.kind.name); put("xPx", a.xPx); put("yPx", a.yPx)
                            put("widthPx", a.widthPx); put("heightPx", a.heightPx); put("label", a.label)
                        }) } })
                    }) } })
                }) }
            })
            put("furniture", JSONArray().apply { project.furniture.forEach { item -> put(JSONObject().apply {
                put("id", item.id); put("name", item.name)
                put("widthMm", item.size.width); put("depthMm", item.size.depth); put("heightMm", item.size.height)
                put("xMm", item.position.x); put("yMm", item.position.y); put("zMm", item.position.z)
                put("rotationDeg", item.rotationDeg); put("materialId", item.materialId); put("colorArgb", item.colorArgb); put("roomId", item.roomId)
            }) } })
            put("assemblies", JSONArray().apply { project.assemblies.forEach { assembly -> put(JSONObject().apply {
                put("id", assembly.id); put("name", assembly.name)
                put("xMm", assembly.position.x); put("yMm", assembly.position.y); put("zMm", assembly.position.z)
                put("parts", JSONArray().apply { assembly.parts.forEach { part -> put(JSONObject().apply {
                    put("id", part.id); put("name", part.name)
                    put("widthMm", part.size.width); put("depthMm", part.size.depth); put("heightMm", part.size.height)
                    put("xMm", part.localPosition.x); put("yMm", part.localPosition.y); put("zMm", part.localPosition.z)
                    put("rotationDeg", part.rotationDeg); put("materialId", part.materialId)
                }) } })
                put("joints", JSONArray().apply { assembly.joints.forEach { joint -> put(JSONObject().apply {
                    put("id", joint.id); put("kind", joint.kind.name)
                    put("firstPartId", joint.firstPartId); put("secondPartId", joint.secondPartId)
                    put("fastenerDiameterMm", joint.fastenerDiameterMm); put("fastenerLengthMm", joint.fastenerLengthMm)
                    put("edgeDistanceMm", joint.edgeDistanceMm); put("materialThicknessMm", joint.materialThicknessMm)
                    put("clearanceMm", joint.clearanceMm); put("notes", joint.notes)
                }) } })
            }) } })
        }
        return root.toString()
    }

    fun save(project: ProjectModel, markAsLast: Boolean = true) {
        val raw = encodeProject(project)
        val editor = preferences.edit().putString(ProjectStorageKeys.project(project.id), raw)
        if (markAsLast) editor.putString("lastProjectId", project.id)
        check(editor.commit()) { "Не удалось сохранить проект" }
    }

    /**
     * Export a portable .mebel3d ZIP package. Unlike legacy JSON export, this package embeds
     * every referenced room photograph, so projects remain usable after moving to another phone.
     */
    fun exportProject(project: ProjectModel, destination: android.net.Uri) {
        val raw = encodeProject(project)
        val root = JSONObject(raw)
        val photoEntries = linkedMapOf<String, ByteArray>()
        var totalPackageBytes = 0L
        val rooms = root.getJSONArray("rooms")
        for (roomIndex in 0 until rooms.length()) {
            val photos = rooms.getJSONObject(roomIndex).optJSONArray("photos") ?: continue
            for (photoIndex in 0 until photos.length()) {
                val photo = photos.getJSONObject(photoIndex)
                require(photoEntries.size < ProjectPackagePolicy.MAX_PHOTO_COUNT) { "В проекте слишком много фотографий для переноса" }
                val photoId = photo.getString("id")
                val entryName = ProjectPackagePolicy.photoEntryName(roomIndex, photoIndex, photoId)
                val sourceUri = android.net.Uri.parse(photo.getString("uri"))
                val bytes = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    BoundedStreamCopy.copy(input, output, MAX_PHOTO_BYTES.toLong())
                    output.toByteArray()
                } ?: error("Не удалось прочитать фотографию ${photoId}. Откройте её заново и повторите экспорт")
                require(bytes.isNotEmpty()) { "Фотография ${photoId} пуста" }
                totalPackageBytes += bytes.size
                require(totalPackageBytes <= ProjectPackagePolicy.MAX_PACKAGE_BYTES) { "Общий размер пакета превышает лимит 100 МБ" }
                require(!photoEntries.containsKey(entryName)) { "Повторяется идентификатор фотографии: $photoId" }
                photoEntries[entryName] = bytes
                photo.put("uri", "bundle:$entryName")
            }
        }
        val projectBytes = root.toString().toByteArray(Charsets.UTF_8)
        require(projectBytes.size.toLong() <= ProjectPackagePolicy.MAX_PROJECT_BYTES) { "Описание проекта превышает лимит 20 МБ" }
        totalPackageBytes += projectBytes.size
        require(totalPackageBytes <= ProjectPackagePolicy.MAX_PACKAGE_BYTES) { "Общий размер пакета превышает лимит 100 МБ" }
        val output = context.contentResolver.openOutputStream(destination)
            ?: error("Не удалось открыть файл для записи")
        output.use { stream ->
            java.util.zip.ZipOutputStream(stream).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("project.json"))
                zip.write(projectBytes)
                zip.closeEntry()
                photoEntries.forEach { (name, bytes) ->
                    zip.putNextEntry(java.util.zip.ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
    }

    /** Imports both portable ZIP packages and legacy standalone JSON files. */
    fun importProject(source: android.net.Uri): ProjectModel {
        val input = context.contentResolver.openInputStream(source)
            ?: error("Не удалось открыть выбранный файл")
        val packageData = input.use { stream ->
            val prefixOutput = java.io.ByteArrayOutputStream(4)
            BoundedStreamCopy.copy(stream, prefixOutput, 4L, 4)
            val prefix = prefixOutput.toByteArray()
            val prefixSize = prefix.size
            val isZip = prefixSize >= 2 && prefix[0] == 'P'.code.toByte() && prefix[1] == 'K'.code.toByte()
            if (!isZip) {
                val output = java.io.ByteArrayOutputStream()
                output.write(prefix, 0, prefixSize)
                BoundedStreamCopy.copy(stream, output, (MAX_PROJECT_BYTES - prefixSize).toLong())
                LegacyOrPackage(output.toByteArray().toString(Charsets.UTF_8), emptyMap())
            } else {
                val entries = linkedMapOf<String, ByteArray>()
                val zip = java.util.zip.ZipInputStream(java.io.SequenceInputStream(
                    java.io.ByteArrayInputStream(prefix, 0, prefixSize), stream
                ))
                var total = 0L
                zip.use { archive ->
                    while (true) {
                        val entry = archive.nextEntry ?: break
                        require(!entry.isDirectory) { "Папки внутри архива не поддерживаются" }
                        val name = entry.name
                        ProjectPackagePolicy.validateEntryName(name)
                        require(!entries.containsKey(name)) { "В архиве повторяется файл: $name" }
                        val output = java.io.ByteArrayOutputStream()
                        val entryLimit = when (name) {
                            "project.json" -> ProjectPackagePolicy.MAX_PROJECT_BYTES
                            else -> ProjectPackagePolicy.MAX_PHOTO_BYTES
                        }
                        val remainingPackageBytes = ProjectPackagePolicy.MAX_PACKAGE_BYTES - total
                        require(remainingPackageBytes > 0L) { "Архив проекта превышает лимит 100 МБ" }
                        val copied = BoundedStreamCopy.copy(archive, output, minOf(entryLimit, remainingPackageBytes))
                        total += copied
                        require(total <= ProjectPackagePolicy.MAX_PACKAGE_BYTES) { "Архив проекта превышает лимит 100 МБ" }
                        ProjectPackagePolicy.validateEntrySize(name, output.size().toLong())
                        entries[name] = output.toByteArray()
                        require(entries.keys.count { it.startsWith("photos/") } <= ProjectPackagePolicy.MAX_PHOTO_COUNT) {
                            "Слишком много фотографий в проекте"
                        }
                        archive.closeEntry()
                    }
                }
                ProjectPackagePolicy.validatePackage(total, entries.keys.count { it.startsWith("photos/") }, entries.containsKey("project.json"))
                val json = entries.remove("project.json") ?: error("В архиве отсутствует project.json")
                LegacyOrPackage(json.toString(Charsets.UTF_8), entries)
            }
        }
        val root = JSONObject(packageData.json)
        val schema = root.optInt("schemaVersion", 1)
        require(schema in 1..14) { "Неподдерживаемая версия формата проекта: $schema" }
        val requestedId = root.optString("id").takeIf { it.isNotBlank() }
            ?: error("В файле отсутствует идентификатор проекта")
        val existingIds = preferences.all.keys.asSequence()
            .filter(ProjectStorageKeys::isUserProjectKey)
            .map { it.removePrefix("project_") }
            .toSet()
        // Re-importing the same package creates a separate copy instead of silently replacing
        // the user's existing local project. All internal asset paths are namespaced by this ID.
        val id = ProjectImportIdentity.resolve(requestedId, existingIds, System.currentTimeMillis().toString())
        root.put("id", id)

        // Extract bundled images into a staging folder and rewrite bundle references to app-owned
        // file URIs. The old local project is not touched until the complete model validates.
        val assetDirectory = java.io.File(context.filesDir, "photo-assets/$id")
        val stagingDirectory = java.io.File(context.filesDir, "photo-assets/.staging-$id-${System.nanoTime()}")
        val stagedAssets = linkedMapOf<String, java.io.File>()
        try {
            val rooms = root.optJSONArray("rooms") ?: JSONArray()
            // Capture package references before they are rewritten to app-private file URIs.
            val bundledPhotoUris = buildList {
                for (roomIndex in 0 until rooms.length()) {
                    val photos = rooms.getJSONObject(roomIndex).optJSONArray("photos") ?: continue
                    for (photoIndex in 0 until photos.length()) {
                        add(photos.getJSONObject(photoIndex).optString("uri"))
                    }
                }
            }
            val referencedBundleEntries = ProjectPackagePolicy.referencedBundleEntries(bundledPhotoUris)
            ProjectPackagePolicy.validateAssetReferences(referencedBundleEntries, packageData.assets.keys)
            for (roomIndex in 0 until rooms.length()) {
                val photos = rooms.getJSONObject(roomIndex).optJSONArray("photos") ?: continue
                for (photoIndex in 0 until photos.length()) {
                    val photo = photos.getJSONObject(photoIndex)
                    val reference = photo.optString("uri")
                    if (!reference.startsWith("bundle:")) continue
                    val entryName = reference.removePrefix("bundle:")
                    val bytes = packageData.assets[entryName] ?: error("В архиве отсутствует фотография $entryName")
                    val photoId = photo.getString("id")
                    // Unique immutable asset names prevent a failed/interrupted import from
                    // overwriting images still referenced by the previously saved project.
                    val targetName = "${safeAssetName(photoId)}-${System.nanoTime()}.bin"
                    require(!stagedAssets.containsKey(targetName)) { "Повторяется идентификатор фотографии: $photoId" }
                    if (!stagingDirectory.exists()) require(stagingDirectory.mkdirs()) { "Не удалось подготовить папку фотографий" }
                    val staged = java.io.File(stagingDirectory, targetName)
                    staged.writeBytes(bytes)
                    stagedAssets[targetName] = staged
                    photo.put("uri", android.net.Uri.fromFile(java.io.File(assetDirectory, targetName)).toString())
                }
            }
            // Asset references were validated before rewriting bundle: URIs above.
        } catch (failure: Throwable) {
            stagingDirectory.deleteRecursively()
            throw failure
        }
        val normalizedRaw = root.toString()
        val key = ProjectStorageKeys.project(id)
        val previous = preferences.getString(key, null)
        val previousLastProjectId = preferences.getString("lastProjectId", null)
        val decoded = try {
            decodeProjectForValidation(normalizedRaw, id).also(ProjectValidator::requireValid)
        } catch (failure: Throwable) {
            stagingDirectory.deleteRecursively()
            throw failure
        }

        // Publish photo assets first; then commit project JSON. Roll back newly published files if
        // the preferences commit fails. Existing assets are retained until the project is stored.
        val backupDirectory = java.io.File(context.filesDir, "photo-assets/.backup-$id-${System.nanoTime()}")
        val replacedAssets = mutableListOf<Pair<java.io.File, java.io.File>>()
        val installedAssets = mutableListOf<java.io.File>()
        try {
            if (stagedAssets.isNotEmpty()) {
                require(assetDirectory.exists() || assetDirectory.mkdirs()) { "Не удалось создать хранилище фотографий" }
                stagedAssets.forEach { (name, staged) ->
                    val target = java.io.File(assetDirectory, name)
                    if (target.exists()) {
                        backupDirectory.mkdirs()
                        val backup = java.io.File(backupDirectory, name)
                        check(target.renameTo(backup)) { "Не удалось подготовить замену фотографии" }
                        replacedAssets += target to backup
                    }
                    check(staged.renameTo(target)) { "Не удалось сохранить фотографию проекта" }
                    installedAssets += target
                }
            }
            val committed = preferences.edit().putString(key, normalizedRaw).putString("lastProjectId", id).commit()
            check(committed) { "Не удалось сохранить импортированный проект" }
        } catch (failure: Throwable) {
            installedAssets.forEach { it.delete() }
            replacedAssets.forEach { (target, backup) -> backup.renameTo(target) }
            stagingDirectory.deleteRecursively()
            backupDirectory.deleteRecursively()
            throw failure
        }
        stagingDirectory.deleteRecursively()
        backupDirectory.deleteRecursively()
        if (packageData.assets.isNotEmpty()) {
            assetDirectory.listFiles()?.filter { it.isFile && it.name !in stagedAssets.keys }?.forEach { it.delete() }
        }
        return decoded
    }

    private data class LegacyOrPackage(val json: String, val assets: Map<String, ByteArray>)

    private fun safeAssetName(id: String): String {
        require(id.isNotBlank() && id.length <= 128 && id.matches(Regex("[A-Za-z0-9._-]+"))) {
            "Недопустимый идентификатор фотографии"
        }
        return id
    }

    private fun decodeProjectForValidation(raw: String, expectedId: String): ProjectModel {
        // Decode directly from the candidate payload. Validation must be side-effect-free:
        // temporarily writing the candidate into SharedPreferences could corrupt the existing
        // project if Android terminated the process between the temporary write and rollback.
        val root = JSONObject(raw)
        require(root.optString("id") == expectedId) { "Идентификатор проекта изменился при импорте" }
        return decodeProject(raw) ?: error("Файл проекта повреждён или содержит неподдерживаемые данные")
    }

    companion object {
        private const val MAX_PROJECT_BYTES = 20 * 1024 * 1024
        private const val MAX_PHOTO_BYTES = 25 * 1024 * 1024
        private const val MAX_PACKAGE_BYTES = 100L * 1024 * 1024
    }

    /** Lists user projects only; internal design-variant snapshots are deliberately hidden. */
    fun listProjects(): List<StoredProjectSummary> {
        val lastId = preferences.getString("lastProjectId", null)
        return preferences.all.entries.asSequence()
            .filter { (key, value) -> ProjectStorageKeys.isUserProjectKey(key) && value is String }
            .mapNotNull { (key, value) ->
                runCatching {
                    val id = key.removePrefix("project_")
                    val root = JSONObject(value as String)
                    if (root.optString("id") != id) return@runCatching null
                    val decoded = decodeProject(value as String) ?: return@runCatching null
                    if (decoded.id != id) return@runCatching null
                    StoredProjectSummary(
                        id = id,
                        name = root.optString("name", "Без названия").ifBlank { "Без названия" },
                        roomCount = root.optJSONArray("rooms")?.length() ?: 0,
                        furnitureCount = root.optJSONArray("furniture")?.length() ?: 0,
                        isLastOpened = id == lastId
                    )
                }.getOrNull()
            }
            .sortedWith(compareByDescending<StoredProjectSummary> { it.isLastOpened }.thenBy { it.name.lowercase() })
            .toList()
    }

    fun deleteProject(projectId: String) {
        require(projectId.isNotBlank() && projectId.length <= 128 && projectId.matches(Regex("[A-Za-z0-9._-]+")))
        val editor = preferences.edit()
        // Design snapshots are stored as hidden project records. Remove them together with
        // their owning project so deleted projects do not leave orphaned data behind.
        ProjectStorageKeys.deletionKeys(projectId, preferences.all.keys).forEach { key -> editor.remove(key) }
        if (preferences.getString("lastProjectId", null) == projectId) {
            val fallback = preferences.all.entries.asSequence()
                .filter { (key, value) -> ProjectStorageKeys.isUserProjectKey(key) && key != ProjectStorageKeys.project(projectId) && value is String }
                .mapNotNull { (key, value) -> runCatching {
                    val id = key.removePrefix("project_")
                    decodeProject(value as String)?.takeIf { it.id == id }?.let { id }
                }.getOrNull() }
                .firstOrNull()
            if (fallback == null) editor.remove("lastProjectId") else editor.putString("lastProjectId", fallback)
        }
        check(editor.commit()) { "Не удалось удалить проект" }
        // Photo assets are private to the app and namespaced by project ID.
        java.io.File(context.filesDir, "photo-assets/$projectId").deleteRecursively()
    }

    /** Opens a project and atomically records it as the last active project. */
    fun openProject(projectId: String): ProjectModel? {
        require(projectId.isNotBlank())
        val project = loadProject(projectId) ?: return null
        check(preferences.edit().putString("lastProjectId", projectId).commit()) {
            "Не удалось обновить последний открытый проект"
        }
        return project
    }

    fun loadLast(): ProjectModel? {
        val id = preferences.getString("lastProjectId", null) ?: return null
        return loadProject(id)
    }

    fun loadProject(projectId: String): ProjectModel? {
        require(projectId.isNotBlank() && projectId.length <= 128 && projectId.matches(Regex("[A-Za-z0-9._-]+")))
        val raw = preferences.getString(ProjectStorageKeys.project(projectId), null) ?: return null
        // A SharedPreferences key is not proof that its payload belongs to that project.
        // Reject mismatched/corrupt records instead of opening another project's state by accident.
        return decodeProject(raw)?.takeIf { it.id == projectId }
    }

    /** The single production JSON decoder, shared by persisted reads and pre-commit import checks. */
    private fun decodeProject(raw: String): ProjectModel? = runCatching {
            val root = JSONObject(raw)
            val schema = root.optInt("schemaVersion", 1)
            require(schema in 1..14) { "Неподдерживаемая версия формата проекта: $schema" }
            val rooms = root.optJSONArray("rooms") ?: JSONArray()
            val furniture = root.optJSONArray("furniture") ?: JSONArray()
            val assemblies = root.optJSONArray("assemblies") ?: JSONArray()
            ProjectModel(
                id = root.getString("id"), name = root.optString("name", "Мой проект"),
                rooms = (0 until rooms.length()).map { i ->
                    val r = rooms.getJSONObject(i); val openings = r.optJSONArray("openings") ?: JSONArray()
                    val engineering = r.optJSONArray("engineering") ?: JSONArray()
                    val photos = r.optJSONArray("photos") ?: JSONArray()
                    RoomModel(r.optString("id", "room-${i + 1}"), r.optString("name", "Помещение ${i + 1}"), r.getInt("widthMm"), r.getInt("depthMm"), r.getInt("heightMm"), r.optInt("wallThicknessMm", 120),
                        (0 until openings.length()).map { j -> openings.getJSONObject(j).let { o ->
                            WallOpening(o.getString("id"), WallSide.valueOf(o.getString("side")), o.getInt("offsetMm"), o.getInt("widthMm"), o.getInt("sillHeightMm"), o.getInt("heightMm"), OpeningKind.valueOf(o.getString("kind")), o.optString("sourcePhotoId").takeIf { it.isNotBlank() && it != "null" }, o.optString("sourceAnnotationId").takeIf { it.isNotBlank() && it != "null" })
                        } },
                        (0 until engineering.length()).map { j -> engineering.getJSONObject(j).let { e ->
                            EngineeringElement(e.getString("id"), e.getString("name"), EngineeringKind.valueOf(e.getString("kind")), Vec3Mm(e.getInt("xMm"), e.getInt("yMm"), e.getInt("zMm")), e.optString("wallSide").takeIf { it.isNotBlank() && it != "null" }?.let(WallSide::valueOf), e.optInt("diameterMm", 0), e.optString("notes"), e.optString("sourcePhotoId").takeIf { it.isNotBlank() && it != "null" }, e.optString("sourceAnnotationId").takeIf { it.isNotBlank() && it != "null" })
                        } },
                        (0 until photos.length()).map { j -> photos.getJSONObject(j).let { ph ->
                            RoomPhotoReference(ph.getString("id"), ph.getString("uri"), ph.optString("wallSide").takeIf { it.isNotBlank() && it != "null" }?.let(WallSide::valueOf), ph.optInt("knownLengthMm").takeIf { it > 0 }, ph.optInt("calibrationPixels").takeIf { it > 0 }, ph.optInt("imageWidthPx").takeIf { it > 0 }, ph.optInt("imageHeightPx").takeIf { it > 0 }, (0 until (ph.optJSONArray("annotations") ?: JSONArray()).length()).map { k -> ph.getJSONArray("annotations").getJSONObject(k).let { a -> PhotoAnnotation(a.getString("id"), PhotoAnnotationKind.valueOf(a.getString("kind")), a.getInt("xPx"), a.getInt("yPx"), a.getInt("widthPx"), a.getInt("heightPx"), a.optString("label")) } }, ph.optInt("wallOriginXPx").takeIf { ph.has("wallOriginXPx") && !ph.isNull("wallOriginXPx") }, ph.optInt("floorLineYPx").takeIf { ph.has("floorLineYPx") && !ph.isNull("floorLineYPx") })
                        } },
                        wallColorArgb = r.optLong("wallColorArgb", 0xFFF0ECE5L),
                        floorColorArgb = r.optLong("floorColorArgb", 0xFFEADCC8L),
                        lightingPreset = r.optString("lightingPreset", "Дневной"),
                        wallMaterialId = r.optString("wallMaterialId").takeIf { it.isNotBlank() && it != "null" },
                        floorMaterialId = r.optString("floorMaterialId").takeIf { it.isNotBlank() && it != "null" },
                        ceilingColorArgb = r.optLong("ceilingColorArgb", 0xFFF8F7F3L),
                        ceilingMaterialId = r.optString("ceilingMaterialId").takeIf { it.isNotBlank() && it != "null" },
                        northWallMaterialId = r.optString("northWallMaterialId").takeIf { it.isNotBlank() && it != "null" },
                        eastWallMaterialId = r.optString("eastWallMaterialId").takeIf { it.isNotBlank() && it != "null" },
                        southWallMaterialId = r.optString("southWallMaterialId").takeIf { it.isNotBlank() && it != "null" },
                        westWallMaterialId = r.optString("westWallMaterialId").takeIf { it.isNotBlank() && it != "null" },
                        lightIntensityPercent = r.optInt("lightIntensityPercent", 72),
                        lightTemperatureKelvin = r.optInt("lightTemperatureKelvin", 4200),
                        shadowStrengthPercent = r.optInt("shadowStrengthPercent", 35)
                    )
                },
                furniture = (0 until furniture.length()).map { i -> furniture.getJSONObject(i).let { f ->
                    FurnitureItem(f.getString("id"), f.getString("name"), Size3Mm(f.getInt("widthMm"), f.getInt("depthMm"), f.getInt("heightMm")),
                        Vec3Mm(f.getInt("xMm"), f.getInt("yMm"), f.optInt("zMm", 0)), f.optInt("rotationDeg", 0), f.optString("materialId", "wood-oak"), f.optLong("colorArgb", 0xFFD8C2A5), f.optString("roomId", "room-main"))
                } },
                activeRoomId = root.optString("activeRoomId", (0 until rooms.length()).firstOrNull()?.let { rooms.getJSONObject(it).optString("id", "room-main") } ?: "room-main"),
                assemblies = (0 until assemblies.length()).map { i -> assemblies.getJSONObject(i).let { a ->
                    val parts = a.optJSONArray("parts") ?: JSONArray()
                    FurnitureAssembly(a.getString("id"), a.getString("name"), Vec3Mm(a.getInt("xMm"), a.getInt("yMm"), a.optInt("zMm", 0)),
                        (0 until parts.length()).map { j -> parts.getJSONObject(j).let { p ->
                            FurniturePart(p.getString("id"), p.getString("name"), Size3Mm(p.getInt("widthMm"), p.getInt("depthMm"), p.getInt("heightMm")),
                                Vec3Mm(p.getInt("xMm"), p.getInt("yMm"), p.optInt("zMm", 0)), p.optInt("rotationDeg", 0), p.optString("materialId", "wood-oak"))
                        } },
                        (0 until (a.optJSONArray("joints") ?: JSONArray()).length()).map { j -> a.getJSONArray("joints").getJSONObject(j).let { joint ->
                            AssemblyJoint(joint.getString("id"), JointKind.valueOf(joint.getString("kind")),
                                joint.getString("firstPartId"), joint.getString("secondPartId"),
                                joint.optInt("fastenerDiameterMm", 0), joint.optInt("fastenerLengthMm", 0),
                                joint.optInt("edgeDistanceMm", 0), joint.optInt("materialThicknessMm", 0),
                                joint.optInt("clearanceMm", 0), joint.optString("notes", ""))
                        } })
                } }
            ).also(ProjectValidator::requireValid)
        }.getOrNull()

    /** Stores variants under a project-specific namespace; variants never leak between projects. */
    fun saveVariants(projectId: String, variants: List<RoomDesignVariant>) {
        val metadataKey = ProjectStorageKeys.variantsMetadata(projectId)
        val oldMetadata = runCatching { JSONArray(preferences.getString(metadataKey, "[]")) }.getOrNull() ?: JSONArray()
        val oldStorageIds = (0 until oldMetadata.length()).mapNotNull {
            oldMetadata.optJSONObject(it)?.optString("storageId")?.takeIf(String::isNotBlank)
        }
        val metadata = JSONArray()
        val plan = ProjectVariantStoragePlanner.plan(projectId, variants.map { it.id }, oldStorageIds)
        val entriesById = plan.entries.associateBy { it.variantId }
        val encodedSnapshots = linkedMapOf<String, String>()

        // Prepare and validate every snapshot before touching SharedPreferences. A bad variant
        // must not leave half of a new set mixed with the previous, still-valid set.
        variants.forEach { variant ->
            require(variant.title.isNotBlank()) { "У варианта должно быть название" }
            require(variant.snapshot.id == projectId) { "Вариант относится к другому проекту" }
            val entry = entriesById.getValue(variant.id)
            val snapshot = variant.snapshot.copy(id = entry.logicalSnapshotId)
            encodedSnapshots[entry.storageKey] = encodeProject(snapshot)
            metadata.put(JSONObject().apply {
                put("id", variant.id); put("title", variant.title); put("note", variant.note)
                put("createdAtEpochMs", variant.createdAtEpochMs); put("storageId", entry.logicalSnapshotId)
                put("sourceProjectId", projectId); put("sourceProjectName", variant.snapshot.name)
            })
        }

        // One synchronous commit publishes the new metadata, all snapshots and stale-key cleanup
        // together. Readers can observe either the previous complete set or the new complete set.
        val editor = preferences.edit()
        encodedSnapshots.forEach { (key, value) -> editor.putString(key, value) }
        editor.putString(metadataKey, metadata.toString())
        plan.obsoleteStorageKeys.forEach(editor::remove)
        check(editor.commit()) { "Не удалось атомарно сохранить варианты проекта" }
    }

    fun loadVariants(projectId: String): List<RoomDesignVariant> {
        require(projectId.isNotBlank() && projectId.length <= 128)
        val metadata = runCatching { JSONArray(preferences.getString(ProjectStorageKeys.variantsMetadata(projectId), "[]")) }.getOrNull() ?: return emptyList()
        return (0 until metadata.length()).mapNotNull { index ->
            runCatching {
                val item = metadata.getJSONObject(index)
                val sourceId = item.optString("sourceProjectId", projectId)
                if (sourceId != projectId) return@runCatching null
                val snapshot = loadProject(item.getString("storageId")) ?: return@runCatching null
                RoomDesignVariant(item.getString("id"), item.getString("title"), item.optString("note"),
                    item.optLong("createdAtEpochMs", 0L), snapshot.copy(
                        id = sourceId,
                        name = item.optString("sourceProjectName", snapshot.name)))
            }.getOrNull()
        }
    }

}
