/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.core.ilm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.LifecycleExecutionState;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;

import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Copy the provided settings from the source to the target index.
 * <p>
 * The target index is generated by a supplier function.
 * This is useful for actions like shrink, rollup or searchable snapshot that create
 * a new index and migrate the ILM execution from the source to the target index.
 */
public class CopySettingsStep extends ClusterStateActionStep {
    public static final String NAME = "copy-settings";

    private static final Logger logger = LogManager.getLogger(CopySettingsStep.class);

    private final String[] settingsKeys;

    private final BiFunction<String, LifecycleExecutionState, String> targetIndexNameSupplier;

    public CopySettingsStep(
        StepKey key,
        StepKey nextStepKey,
        BiFunction<String, LifecycleExecutionState, String> targetIndexNameSupplier,
        String... settingsKeys
    ) {
        super(key, nextStepKey);
        Objects.requireNonNull(settingsKeys);
        this.settingsKeys = settingsKeys;
        this.targetIndexNameSupplier = targetIndexNameSupplier;
    }

    @Override
    public boolean isRetryable() {
        return true;
    }

    public String[] getSettingsKeys() {
        return settingsKeys;
    }

    BiFunction<String, LifecycleExecutionState, String> getTargetIndexNameSupplier() {
        return targetIndexNameSupplier;
    };

    @Override
    public ClusterState performAction(Index index, ClusterState clusterState) {
        String sourceIndexName = index.getName();
        IndexMetadata sourceIndexMetadata = clusterState.metadata().index(sourceIndexName);
        if (sourceIndexMetadata == null) {
            // Index must have been since deleted, ignore it
            logger.debug("[{}] lifecycle action for index [{}] executed but index no longer exists", getKey().action(), sourceIndexName);
            return clusterState;
        }

        if (settingsKeys == null || settingsKeys.length == 0) {
            return clusterState;
        }

        String targetIndexName = targetIndexNameSupplier.apply(sourceIndexName, sourceIndexMetadata.getLifecycleExecutionState());
        IndexMetadata targetIndexMetadata = clusterState.metadata().index(targetIndexName);
        if (targetIndexMetadata == null) {
            String errorMessage = String.format(
                Locale.ROOT,
                "index [%s] is being referenced by ILM action [%s] on step [%s] but " + "it doesn't exist",
                targetIndexName,
                getKey().action(),
                getKey().name()
            );
            logger.debug(errorMessage);
            throw new IllegalStateException(errorMessage);
        }

        Settings.Builder settings = Settings.builder().put(targetIndexMetadata.getSettings());
        for (String key : settingsKeys) {
            String value = sourceIndexMetadata.getSettings().get(key);
            settings.put(key, value);
        }

        Metadata.Builder newMetaData = Metadata.builder(clusterState.getMetadata())
            .put(
                IndexMetadata.builder(targetIndexMetadata).settingsVersion(targetIndexMetadata.getSettingsVersion() + 1).settings(settings)
            );
        return ClusterState.builder(clusterState).metadata(newMetaData).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (super.equals(o) == false) {
            return false;
        }
        CopySettingsStep that = (CopySettingsStep) o;
        return super.equals(o)
            && Objects.equals(targetIndexNameSupplier, that.targetIndexNameSupplier)
            && Objects.equals(settingsKeys, that.settingsKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), targetIndexNameSupplier, settingsKeys);
    }
}
