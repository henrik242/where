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
import no.synth.where.util.NamingUtils
import org.maplibre.android.geometry.LatLng
import java.io.File
import java.lang.reflect.Type

private class LatLngSerializer : JsonSerializer<LatLng> {
    override fun serialize(src: LatLng, typeOfSrc: Type, context: JsonSerializationContext) =
        JsonObject().apply {
            addProperty("latitude", src.latitude)
            addProperty("longitude", src.longitude)
        }
}

private class LatLngDeserializer : JsonDeserializer<LatLng> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext) =
        json.asJsonObject.let {
            LatLng(it.get("latitude").asDouble, it.get("longitude").asDouble)
        }
}

class SavedPointsRepository private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LatLng::class.java, LatLngSerializer())
        .registerTypeAdapter(LatLng::class.java, LatLngDeserializer())
        .create()
    private val pointsFile: File = File(context.filesDir, "saved_points.json")

    val savedPoints = mutableStateListOf<SavedPoint>()

    init {
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

    fun addPoint(name: String, latLng: LatLng, description: String = "", color: String = "#FF5722") {
        val uniqueName = NamingUtils.makeUnique(name, savedPoints.map { it.name })
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
            val otherNames = savedPoints.filter { it.id != pointId }.map { it.name }
            val uniqueName = NamingUtils.makeUnique(name, otherNames)

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

