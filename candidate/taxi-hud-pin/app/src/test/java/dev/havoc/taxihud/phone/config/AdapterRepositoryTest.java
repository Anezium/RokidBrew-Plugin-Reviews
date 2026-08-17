package dev.havoc.taxihud.phone.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import android.content.Context;
import dev.havoc.taxihud.phone.parse.NotificationAdapterEngineTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public final class AdapterRepositoryTest {
    private AdapterRepository repository;
    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        repository = new AdapterRepository(context);
        repository.resetImported();
    }
    @Test public void defaultsToBuiltInAdapter() {
        assertEquals(1, repository.adapters().size());
        assertTrue(repository.handlesPackage("ru.yandex.go"));
    }
    @Test public void mergesMultipleImportedAdaptersAndCanToggleEach() {
        repository.importJson(NotificationAdapterEngineTest.customBundle(
                "local-cab", "com.example.localcab"));
        repository.importJson(NotificationAdapterEngineTest.customBundle(
                "uber-demo", "com.example.uber"));
        assertEquals(3, repository.adapters().size());
        repository.setEnabled("local-cab", false);
        assertFalse(repository.handlesPackage("com.example.localcab"));
        assertTrue(repository.handlesPackage("com.example.uber"));
    }
    @Test public void invalidImportIsAtomic() {
        repository.importJson(NotificationAdapterEngineTest.customBundle(
                "local-cab", "com.example.localcab"));
        try {
            repository.importJson("{\"schemaVersion\":1,\"adapters\":[{\"id\":\"bad\","
                    + "\"displayName\":\"Bad\",\"enabled\":true,"
                    + "\"packages\":[\"bad package\"],\"fieldRules\":[]}]}" );
        } catch (IllegalArgumentException expected) { }
        assertTrue(repository.handlesPackage("com.example.localcab"));
        assertEquals(2, repository.adapters().size());
    }

    @Test public void resetRemovesImportedOverridesButPreservesBuiltInOverride() {
        repository.setEnabled("yandex-go", false);
        repository.importJson(NotificationAdapterEngineTest.customBundle(
                "local-cab", "com.example.localcab"));
        repository.setEnabled("local-cab", false);

        repository.resetImported();

        assertEquals(1, repository.adapters().size());
        assertFalse(repository.handlesPackage("ru.yandex.go"));
        assertFalse(repository.handlesPackage("com.example.localcab"));
    }
}
