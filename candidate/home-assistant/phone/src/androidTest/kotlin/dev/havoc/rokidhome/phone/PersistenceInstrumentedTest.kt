package dev.havoc.rokidhome.phone

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.havoc.rokidhome.phone.data.AppDatabase
import dev.havoc.rokidhome.phone.data.ConfigurationRepository
import dev.havoc.rokidhome.phone.security.CredentialStore
import dev.havoc.rokidhome.phone.security.Credentials
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceInstrumentedTest {
    @Test fun credentialsRoundTripThroughAndroidKeystore() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = CredentialStore(context)
        val expected = Credentials("https://ha.example.org", "secret-ha-token", "secret-rokid-token")
        store.save(expected)
        assertEquals(expected, CredentialStore(context).load())
    }

    @Test fun failedRoomPublicationKeepsLastSnapshot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repository = ConfigurationRepository(database)
        repository.ensureSeeded()
        val accepted = repository.publish().getOrThrow()
        repository.deletePage("home")
        assertTrue(repository.publish().isFailure)
        assertEquals(accepted, repository.currentPublished())
        database.close()
    }
}
