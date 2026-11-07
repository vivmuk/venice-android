package ai.venice.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GallerySaver {

    suspend fun saveImageToGallery(context: Context, imageFile: File, title: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!imageFile.exists()) {
                    throw IllegalArgumentException("Source image file does not exist")
                }

                val resolver = context.contentResolver
                val filename = "Venice_${System.currentTimeMillis()}.png"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.TITLE, title.take(64))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_PICTURES}/Venice"
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("Could not create MediaStore entry")

                resolver.openOutputStream(uri)?.use { outStream ->
                    imageFile.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                } ?: throw IllegalStateException("Could not open MediaStore output stream")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                "Saved to Pictures/Venice/$filename"
            }
        }
}
