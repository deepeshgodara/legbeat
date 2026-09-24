package com.legbeat.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class MapLayerType(
    val id: String,
    val displayName: String,
    val description: String,
    val isRaster: Boolean,
    val tileOrStyleUrl: String
) {
    STREET(
        id = "street",
        displayName = "Street View",
        description = "OpenFreeMap Liberty vector roads and cycling paths",
        isRaster = false,
        tileOrStyleUrl = "https://tiles.openfreemap.org/styles/liberty"
    ),
    SATELLITE(
        id = "satellite",
        displayName = "Satellite View",
        description = "Global high-resolution satellite imagery (ArcGIS)",
        isRaster = true,
        tileOrStyleUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
    ),
    TERRAIN(
        id = "terrain",
        displayName = "Topographic Terrain",
        description = "OpenTopoMap contours and elevation shading",
        isRaster = true,
        tileOrStyleUrl = "https://tile.opentopomap.org/{z}/{x}/{y}.png"
    ),
    MINIMAL(
        id = "minimal",
        displayName = "Minimal Dark",
        description = "High-contrast dark vector theme (OpenFreeMap Positron)",
        isRaster = false,
        tileOrStyleUrl = "https://tiles.openfreemap.org/styles/positron"
    );

    fun buildStyleJson(): String {
        return """
        {
          "version": 8,
          "sources": {
            "raster-tiles": {
              "type": "raster",
              "tiles": ["$tileOrStyleUrl"],
              "tileSize": 256
            }
          },
          "layers": [
            {
              "id": "raster-layer",
              "type": "raster",
              "source": "raster-tiles"
            }
          ]
        }
        """.trimIndent()
    }

    companion object {
        fun fromId(id: String?): MapLayerType =
            entries.firstOrNull { it.id == id } ?: STREET
    }
}

@Singleton
class FlyoverSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("legbeat_flyover_settings", Context.MODE_PRIVATE)

    private val _cameraTiltAngle = MutableStateFlow(prefs.getFloat(KEY_TILT_ANGLE, 65.0f))
    val cameraTiltAngle: StateFlow<Float> = _cameraTiltAngle.asStateFlow()

    private val _replaySpeed = MutableStateFlow(prefs.getInt(KEY_REPLAY_SPEED, 2))
    val replaySpeed: StateFlow<Int> = _replaySpeed.asStateFlow()

    private val _mapLayer = MutableStateFlow(MapLayerType.fromId(prefs.getString(KEY_MAP_LAYER, MapLayerType.STREET.id)))
    val mapLayer: StateFlow<MapLayerType> = _mapLayer.asStateFlow()

    private val _targetVideoDurationSeconds = MutableStateFlow(prefs.getInt(KEY_VIDEO_DURATION, 30))
    val targetVideoDurationSeconds: StateFlow<Int> = _targetVideoDurationSeconds.asStateFlow()

    fun setCameraTiltAngle(angle: Float) {
        val clamped = angle.coerceIn(30.0f, 85.0f)
        _cameraTiltAngle.value = clamped
        prefs.edit().putFloat(KEY_TILT_ANGLE, clamped).apply()
    }

    fun setReplaySpeed(speed: Int) {
        val validSpeed = when (speed) {
            1, 2, 5, 10, 25, 50, 100 -> speed
            4 -> 5
            8 -> 10
            else -> 2
        }
        _replaySpeed.value = validSpeed
        prefs.edit().putInt(KEY_REPLAY_SPEED, validSpeed).apply()
    }

    fun setMapLayer(layer: MapLayerType) {
        _mapLayer.value = layer
        prefs.edit().putString(KEY_MAP_LAYER, layer.id).apply()
    }

    fun setTargetVideoDurationSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(10, 120)
        _targetVideoDurationSeconds.value = clamped
        prefs.edit().putInt(KEY_VIDEO_DURATION, clamped).apply()
    }

    companion object {
        private const val KEY_TILT_ANGLE = "flyover_camera_tilt_angle"
        private const val KEY_REPLAY_SPEED = "flyover_replay_speed"
        private const val KEY_MAP_LAYER = "flyover_map_layer"
        private const val KEY_VIDEO_DURATION = "flyover_video_duration"
    }
}
