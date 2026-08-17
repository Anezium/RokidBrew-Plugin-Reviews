package dev.havoc.rokidhome.phone.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Entity(tableName = "pages")
@Serializable
data class PageEntity(@PrimaryKey val id: String, val name: String, val position: Int)

@Entity(tableName = "widgets", foreignKeys = [ForeignKey(entity = PageEntity::class, parentColumns = ["id"], childColumns = ["pageId"], onDelete = ForeignKey.CASCADE)], indices = [Index("pageId")])
@Serializable
data class WidgetEntity(@PrimaryKey val id: String, val pageId: String, val type: String, val position: Int)

@Entity(tableName = "actions", primaryKeys = ["widgetId", "slot"], foreignKeys = [ForeignKey(entity = WidgetEntity::class, parentColumns = ["id"], childColumns = ["widgetId"], onDelete = ForeignKey.CASCADE)], indices = [Index("widgetId")])
@Serializable
data class ActionEntity(val widgetId: String, val slot: String, val json: String)

@Entity(tableName = "bindings", primaryKeys = ["widgetId", "slot"], foreignKeys = [ForeignKey(entity = WidgetEntity::class, parentColumns = ["id"], childColumns = ["widgetId"], onDelete = ForeignKey.CASCADE)], indices = [Index("widgetId")])
@Serializable
data class BindingEntity(val widgetId: String, val slot: String, val json: String)

@Entity(tableName = "context_rules")
@Serializable
data class ContextRuleEntity(
    @PrimaryKey val id: String,
    val enabled: Boolean,
    val conditionTemplate: String,
    val pageId: String,
    val priority: Int,
    val position: Int,
    val activateAfterMs: Long,
    val deactivateAfterMs: Long,
)

@Entity(tableName = "published_configuration")
data class PublishedConfigEntity(@PrimaryKey val singleton: Int = 1, val version: Long, val checksum: String, val json: String)

data class PageWithWidgets(
    @Embedded val page: PageEntity,
    @Relation(parentColumn = "id", entityColumn = "pageId", entity = WidgetEntity::class)
    val widgets: List<WidgetWithDetails>,
)

data class WidgetWithDetails(
    @Embedded val widget: WidgetEntity,
    @Relation(parentColumn = "id", entityColumn = "widgetId") val actions: List<ActionEntity>,
    @Relation(parentColumn = "id", entityColumn = "widgetId") val bindings: List<BindingEntity>,
)

@Dao
interface ConfigurationDao {
    @Transaction @Query("SELECT * FROM pages ORDER BY position")
    fun observePages(): Flow<List<PageWithWidgets>>

    @Transaction @Query("SELECT * FROM pages ORDER BY position")
    suspend fun pages(): List<PageWithWidgets>

    @Query("SELECT * FROM context_rules ORDER BY priority DESC, position")
    fun observeRules(): Flow<List<ContextRuleEntity>>

    @Query("SELECT * FROM context_rules ORDER BY priority DESC, position")
    suspend fun rules(): List<ContextRuleEntity>

    @Query("SELECT * FROM published_configuration WHERE singleton = 1")
    fun observePublished(): Flow<PublishedConfigEntity?>

    @Query("SELECT * FROM published_configuration WHERE singleton = 1")
    suspend fun published(): PublishedConfigEntity?

    @Upsert suspend fun putPage(value: PageEntity)
    @Upsert suspend fun putWidget(value: WidgetEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putActions(values: List<ActionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBindings(values: List<BindingEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRule(value: ContextRuleEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPublished(value: PublishedConfigEntity)
    @Query("DELETE FROM pages WHERE id = :id") suspend fun deletePage(id: String)
    @Query("DELETE FROM widgets WHERE id = :id") suspend fun deleteWidget(id: String)
    @Query("DELETE FROM actions WHERE widgetId = :widgetId AND slot = :slot")
    suspend fun deleteAction(widgetId: String, slot: String)
    @Query("DELETE FROM context_rules WHERE id = :id") suspend fun deleteRule(id: String)
    @Query("DELETE FROM published_configuration") suspend fun clearPublished()
    @Query("DELETE FROM context_rules") suspend fun clearRules()
    @Query("DELETE FROM pages") suspend fun clearPages()
}

@Database(
    entities = [PageEntity::class, WidgetEntity::class, ActionEntity::class, BindingEntity::class, ContextRuleEntity::class, PublishedConfigEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun configurationDao(): ConfigurationDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "rokid-home.db")
                .addMigrations()
                .build()
                .also { instance = it }
        }
    }
}
