package com.legbeat.fit

import com.legbeat.core.model.CadenceSample
import com.legbeat.core.model.Ride
import java.io.File

/**
 * Interface for encoding ride sessions into standard Garmin FIT binary files (.fit).
 * Files produced are interoperable with Strava, TrainingPeaks, and Garmin Connect.
 */
interface FitActivityEncoder {
    /**
     * Serializes a completed [ride] and its timestamped [samples] into [outputFile].
     * @return Result containing the generated FIT file on success.
     */
    fun encode(
        outputFile: File,
        ride: Ride,
        samples: List<CadenceSample>
    ): Result<File>
}
