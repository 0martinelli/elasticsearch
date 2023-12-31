/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.core.ml.inference.persistence;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xpack.core.template.TemplateUtils;

import java.util.Map;

/**
 * Class containing the index constants so that the index version, name, and prefix are available to a wider audience.
 */
public final class InferenceIndexConstants {

    /**
     * When incrementing the index version please also update
     * any use of the constant in x-pack/qa/
     *
     * version: 7.8.0:
     *  - adds inference_config definition to trained model config
     *
     * version: 7.10.0: 000003
     *  - adds trained_model_metadata object
     *
     * version: 7.16.0: 000004
     *  - adds model_size_bytes field as a estimated_heap_memory_usage_bytes replacement
     *
     * version: 8.0.0: 000005
     *  - adds binary_definition for TrainedModelDefinitionDoc
     */
    public static final String INDEX_VERSION = "000005";
    public static final String INDEX_NAME_PREFIX = ".ml-inference-";
    public static final String INDEX_PATTERN = INDEX_NAME_PREFIX + "*";
    public static final String LATEST_INDEX_NAME = INDEX_NAME_PREFIX + INDEX_VERSION;
    public static final ParseField DOC_TYPE = new ParseField("doc_type");

    private static final String NATIVE_INDEX_PREFIX = INDEX_NAME_PREFIX + "native-";
    private static final String NATIVE_INDEX_VERSION = "000001";
    private static final String NATIVE_LATEST_INDEX = NATIVE_INDEX_PREFIX + NATIVE_INDEX_VERSION;

    private static final String MAPPINGS_VERSION_VARIABLE = "xpack.ml.version";
    public static final int INFERENCE_INDEX_MAPPINGS_VERSION = 1;

    public static String mapping() {
        return TemplateUtils.loadTemplate(
            "/ml/inference_index_mappings.json",
            Version.CURRENT.toString(),
            MAPPINGS_VERSION_VARIABLE,
            Map.of("xpack.ml.managed.index.version", Integer.toString(INFERENCE_INDEX_MAPPINGS_VERSION))
        );
    }

    public static String nativeDefinitionStore() {
        return NATIVE_LATEST_INDEX;
    }

    public static Settings settings() {
        return Settings.builder()
            .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
            .put(IndexMetadata.SETTING_AUTO_EXPAND_REPLICAS, "0-1")
            .build();
    }

    private InferenceIndexConstants() {}
}
