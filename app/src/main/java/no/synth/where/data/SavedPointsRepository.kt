package no.synth.where.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import java.io.File
import java.lang.reflect.Type

// Custom serializer for LatLng
class LatLngSerializer : JsonSerializer<LatLng> {
    override fun serialize(src: LatLng, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val obj = JsonObject()
        obj.addProperty("latitude", src.latitude)
        obj.addProperty("longitude", src.longitude)
        return obj
    }
}

// Custom deserializer for LatLng
class LatLngDeserializer : JsonDeserializer<LatLng> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LatLng {
        val obj = json.asJsonObject
        return LatLng(
            obj.get("latitude").asDouble,
            obj.get("longitude").asDouble
        )
    }
}

class SavedPointsRepository private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LatLng::class.java, LatLngSerializer())
        .registerTypeAdapter(LatLng::class.java, LatLngDeserializer())
        .create()
    private val pointsFile = File(context.filesDir, "saved_points.json")

    val savedPoints = mutableStateListOf<SavedPoint>()

    init {
        // Load points synchronously to ensure they're available immediately
        try {
            if (pointsFile.exists()) {
                val json = pointsFile.readText()
                val type = object : TypeToken<List<SavedPoint>>() {}.type
                val points: List<SavedPoint> = gson.fromJson(json, type)
                savedPoints.addAll(points)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun savePoints() {
        scope.launch {
            try {
                val json = gson.toJson(savedPoints)
                pointsFile.writeText(json)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getUniquePointName(baseName: String): String {
        val existingNames = savedPoints.map { it.name }.toSet()
        if (!existingNames.contains(baseName)) {
            return baseName
        }

        var counter = 2
        while (existingNames.contains("$baseName ($counter)")) {
            counter++
        }
        return "$baseName ($counter)"
    }

    fun addPoint(name: String, latLng: LatLng, description: String = "", color: String = "#FF5722") {
        val uniqueName = getUniquePointName(name)
        val point = SavedPoint(
            id = java.util.UUID.randomUUID().toString(),
            name = uniqueName,
            latLng = latLng,
            description = description,
            color = color
        )
        savedPoints.add(point)
        savePoints()
    }

    fun deletePoint(pointId: String) {
        savedPoints.removeAll { it.id == pointId }
        savePoints()
    }

    fun updatePoint(pointId: String, name: String, description: String, color: String) {
        val index = savedPoints.indexOfFirst { it.id == pointId }
        if (index != -1) {
            val point = savedPoints[index]
            // Get unique name, but exclude the current point from the check
            val otherNames = savedPoints.filter { it.id != pointId }.map { it.name }.toSet()
            val uniqueName = if (otherNames.contains(name)) {
                var counter = 2
                while (otherNames.contains("$name ($counter)")) {
                    counter++
                }
                "$name ($counter)"
            } else {
                name
            }

            savedPoints[index] = point.copy(
                name = uniqueName,
                description = description,
                color = color
            )
            savePoints()
        }
    }

    companion object {
        @Volatile
        private var instance: SavedPointsRepository? = null

        fun getInstance(context: Context): SavedPointsRepository {
            return instance ?: synchronized(this) {
                instance ?: SavedPointsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

