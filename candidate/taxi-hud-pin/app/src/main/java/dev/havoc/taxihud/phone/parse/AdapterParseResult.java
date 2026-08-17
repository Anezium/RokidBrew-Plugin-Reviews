package dev.havoc.taxihud.phone.parse;

import dev.havoc.taxihud.phone.config.NotificationAdapterConfig;

public final class AdapterParseResult {
    public final NotificationAdapterConfig adapter;
    public final TaxiUpdate update;

    private AdapterParseResult(NotificationAdapterConfig adapter, TaxiUpdate update) {
        this.adapter = adapter;
        this.update = update == null ? TaxiUpdate.noMatch() : update;
    }

    public static AdapterParseResult matched(
            NotificationAdapterConfig adapter, TaxiUpdate update) {
        return new AdapterParseResult(adapter, update);
    }

    public static AdapterParseResult noMatch() {
        return new AdapterParseResult(null, TaxiUpdate.noMatch());
    }

    public boolean matched() {
        return adapter != null && update.kind != TaxiUpdate.Kind.NO_MATCH;
    }
}
