package net.onelitefeather.skygridng.platforms.olf;

import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import net.onelitefeather.skygridng.platforms.common.SkyGridNgPlugin;

import java.util.List;
import java.util.Set;

/**
 * Composition root for the OneLiteFeather network distribution. It reuses the base SkyGrid-ng wiring
 * and layers the OLF feature module on top, then enables every bound {@link OlfFeature} with the
 * plugin lifecycle. The OLF only mechanics ship in this module, which is not published publicly.
 *
 * Not final: MockBukkit v4 loads the plugin by subclassing it.
 */
public class OlfNetworkPlugin extends SkyGridNgPlugin {

    private static final Key<Set<OlfFeature>> FEATURES = Key.get(new TypeLiteral<Set<OlfFeature>>() {});

    private Set<OlfFeature> features;

    @Override
    protected List<Module> modules() {
        List<Module> modules = super.modules();
        modules.add(new OlfModule());
        return modules;
    }

    @Override
    protected void onExtensionEnable() {
        features = injector().getInstance(FEATURES);
        for (OlfFeature feature : features) {
            feature.enable();
            getLogger().info("[OLF] Enabled feature: " + feature.id() + ".");
        }
    }

    @Override
    protected void onExtensionDisable() {
        if (features != null) {
            features.forEach(OlfFeature::disable);
        }
    }
}
