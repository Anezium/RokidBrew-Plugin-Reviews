package dev.havoc.taxihud.phone.config;

import java.util.Collections;
import java.util.List;

/** Validated, non-persisted summary shown before an adapter bundle is imported. */
public final class AdapterImportPreview {
    public final int adapterCount;
    public final List<String> packages;

    AdapterImportPreview(int adapterCount, List<String> packages) {
        this.adapterCount = adapterCount;
        this.packages = Collections.unmodifiableList(packages);
    }
}
