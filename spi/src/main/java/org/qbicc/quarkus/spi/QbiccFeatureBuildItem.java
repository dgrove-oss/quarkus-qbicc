package org.qbicc.quarkus.spi;

import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A Qbicc feature.
 */
public final class QbiccFeatureBuildItem extends MultiBuildItem {
    private final Object feature; // Will be an org.qbicc.plugin.initializationcontrol.QbiccFeature

    public QbiccFeatureBuildItem(final Object feature) {
        this.feature = feature;
    }

    public Object getFeature() {
        return feature;
    }
}
