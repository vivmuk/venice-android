package ai.venice.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val modelId: String,
    val temperature: Float = 0.7f,
    val reasoningEffort: String = "none",
    val webSearchEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val reasoningContent: String = "",
    val modelId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

@Entity(tableName = "generated_images")
data class GeneratedImageEntity(
    @PrimaryKey
    val id: String,
    val prompt: String,
    val modelId: String,
    val localFilePath: String,
    val size: String = "1024x1024",
    val createdAt: Long = System.currentTimeMillis()
)
