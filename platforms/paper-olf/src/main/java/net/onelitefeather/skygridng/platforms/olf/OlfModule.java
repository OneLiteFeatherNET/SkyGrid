package net.onelitefeather.skygridng.platforms.olf;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

/**
 * Binds the OneLiteFeather network features. Each mechanic is one {@link OlfFeature} added to the set
 * binder; {@link OlfNetworkPlugin} enables them all. Add a new mechanic by adding one binding line.
 */
final class OlfModule extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder<OlfFeature> features = Multibinder.newSetBinder(binder(), OlfFeature.class);
        features.addBinding().to(NetworkStartupReportFeature.class);
        // Future OLF mechanics (challenges, ...) add their binding here.
    }
}
