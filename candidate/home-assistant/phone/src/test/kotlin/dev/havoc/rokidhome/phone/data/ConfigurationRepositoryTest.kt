package dev.havoc.rokidhome.phone.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.havoc.rokidhome.shared.model.ValueSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfigurationRepositoryTest {
    @Test fun publishIsAtomicAndVersionIsMonotonic() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repository = ConfigurationRepository(database)
        repository.ensureSeeded()

        val first = repository.publish().getOrThrow()
        val second = repository.publish().getOrThrow()

        assertEquals(first.configVersion + 1, second.configVersion)
        database.configurationDao().pages().forEach { repository.deletePage(it.page.id) }
        assertTrue(repository.publish().isFailure)
        assertEquals(second, repository.currentPublished())
        database.close()
    }

    @Test fun starterConfigurationIsNeutralIdempotentAndPreservesUserPages() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repository = ConfigurationRepository(database)
        val userPageId = repository.addPage("My dashboard")

        assertTrue(repository.installStarterConfiguration())
        assertFalse(repository.installStarterConfiguration())

        val published = repository.publish().getOrThrow()
        assertEquals(userPageId, published.defaultPageId)
        assertNotNull(published.pages.firstOrNull { it.id == userPageId })
        val guide = published.pages.first { it.id == "starter-guide-v1" }
        assertEquals(2, guide.widgets.size)
        assertEquals("Connection", (guide.widgets[0].label as ValueSource.Literal).value)
        assertEquals("Dashboard", (guide.widgets[1].label as ValueSource.Literal).value)
        assertTrue(published.contextRules.isEmpty())
        database.close()
    }

    @Test fun cleanupRemovesOldStarterContentButKeepsUserContent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = database.configurationDao()
        dao.putPage(PageEntity("starter-old-v1", "Old starter", 0))
        dao.putWidget(WidgetEntity("starter-old-widget-v1", "starter-old-v1", "STATUS", 0))
        dao.putPage(PageEntity("page-user", "User page", 1))
        dao.putRule(ContextRuleEntity("starter-old-rule-v1", true, "{{ true }}", "starter-old-v1", 1, 0, 0, 0))
        val repository = ConfigurationRepository(database)

        assertTrue(repository.installStarterConfiguration(restoreMissingStarterContent = false))

        val published = repository.publish().getOrThrow()
        assertEquals(listOf("page-user"), published.pages.map { it.id })
        assertTrue(published.contextRules.isEmpty())
        database.close()
    }

    @Test fun cleanupLeavesAUsableGuideWhenOnlyOldStarterContentExists() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = database.configurationDao()
        dao.putPage(PageEntity("starter-old-v1", "Old starter", 0))
        val repository = ConfigurationRepository(database)

        assertTrue(repository.installStarterConfiguration(restoreMissingStarterContent = false))

        val published = repository.publish().getOrThrow()
        assertEquals(listOf("starter-guide-v1"), published.pages.map { it.id })
        assertNull(published.pages.single().widgets.singleOrNull { it.action != null })
        database.close()
    }

    @Test fun portableBackupReplacesEditableConfigurationAndPublishesIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sourceDatabase = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        val source = ConfigurationRepository(sourceDatabase)
        val pageId = source.addPage("Portable dashboard")
        val widgetId = source.addWidget(
            pageId,
            dev.havoc.rokidhome.shared.model.WidgetType.TEXT,
        )
        source.saveBinding(widgetId, "primary", ValueSource.Literal("Restored"))
        val backup = source.exportBackup()

        val targetDatabase = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        val target = ConfigurationRepository(targetDatabase)
        target.ensureSeeded()
        target.importBackup(backup)

        val restored = target.currentPublished()
        assertNotNull(restored)
        assertEquals(listOf("Portable dashboard"), restored!!.pages.map { it.name })
        assertEquals(1, restored.pages.single().widgets.size)
        sourceDatabase.close()
        targetDatabase.close()
    }
}
