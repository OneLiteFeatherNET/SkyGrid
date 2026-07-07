package net.onelitefeather.skygridng.platforms.olf;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

/**
 * Binds the OneLiteFeather network features. Each mechanic is one {@link OlfFeature} added to the set
 * binder; {@code SkyGridNgPlugin} enables them all. Add a new mechanic by adding one binding line.
 */
public final class OlfModule extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder<OlfFeature> features = Multibinder.newSetBinder(binder(), OlfFeature.class);
        features.addBinding().to(NetworkStartupReportFeature.class);
        features.addBinding().to(ChallengeFeature.class);
        // Shows the active challenge's task progress as a live per-player scoreboard sidebar.
        features.addBinding().to(ChallengeSidebarFeature.class);
        // Future OLF mechanics add their binding here.
    }
}
