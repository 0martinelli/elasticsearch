/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.upgrades;

import com.carrotsearch.randomizedtesting.annotations.Name;

import org.elasticsearch.action.admin.cluster.migration.TransportGetFeatureUpgradeStatusAction;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.test.XContentTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class FeatureUpgradeIT extends ParameterizedRollingUpgradeTestCase {

    public FeatureUpgradeIT(@Name("upgradeNode") Integer upgradeNode) {
        super(upgradeNode);
    }

    public void testGetFeatureUpgradeStatus() throws Exception {

        final String systemIndexWarning = "this request accesses system indices: [.tasks], but in a future major version, direct "
            + "access to system indices will be prevented by default";
        if (isOldCluster()) {
            // setup - put something in the tasks index
            // create index
            Request createTestIndex = new Request("PUT", "/feature_test_index_old");
            createTestIndex.setJsonEntity("{\"settings\": {\"index.number_of_replicas\": 0}}");
            client().performRequest(createTestIndex);

            Request bulk = new Request("POST", "/_bulk");
            bulk.addParameter("refresh", "true");
            bulk.setJsonEntity("""
                {"index": {"_index": "feature_test_index_old"}}
                {"f1": "v1", "f2": "v2"}
                """);
            client().performRequest(bulk);

            // start a async reindex job
            Request reindex = new Request("POST", "/_reindex");
            reindex.setJsonEntity("""
                {
                  "source":{
                    "index":"feature_test_index_old"
                  },
                  "dest":{
                    "index":"feature_test_index_reindex"
                  }
                }""");
            reindex.addParameter("wait_for_completion", "false");
            Map<String, Object> response = entityAsMap(client().performRequest(reindex));
            String taskId = (String) response.get("task");

            // wait for task
            Request getTask = new Request("GET", "/_tasks/" + taskId);
            getTask.addParameter("wait_for_completion", "true");
            client().performRequest(getTask);

            // make sure .tasks index exists
            Request getTasksIndex = new Request("GET", "/.tasks");
            getTasksIndex.setOptions(expectVersionSpecificWarnings(v -> {
                v.current(systemIndexWarning);
                v.compatible(systemIndexWarning);
            }));
            getTasksIndex.addParameter("allow_no_indices", "false");

            assertBusy(() -> {
                try {
                    assertThat(client().performRequest(getTasksIndex).getStatusLine().getStatusCode(), is(200));
                } catch (ResponseException e) {
                    throw new AssertionError(".tasks index does not exist yet");
                }
            });

        } else if (isUpgradedCluster()) {
            // check results
            assertBusy(() -> {
                Request clusterStateRequest = new Request("GET", "/_migration/system_features");
                XContentTestUtils.JsonMapView view = new XContentTestUtils.JsonMapView(
                    entityAsMap(client().performRequest(clusterStateRequest))
                );

                List<Map<String, Object>> features = view.get("features");
                Map<String, Object> feature = features.stream()
                    .filter(e -> "tasks".equals(e.get("feature_name")))
                    .findFirst()
                    .orElse(Collections.emptyMap());

                assertThat(feature, aMapWithSize(4));
                assertThat(feature.get("minimum_index_version"), equalTo(getOldClusterIndexVersion().toString()));
                if (getOldClusterVersion().before(TransportGetFeatureUpgradeStatusAction.NO_UPGRADE_REQUIRED_VERSION)) {
                    assertThat(feature.get("migration_status"), equalTo("MIGRATION_NEEDED"));
                } else {
                    assertThat(feature.get("migration_status"), equalTo("NO_MIGRATION_NEEDED"));
                }
            });
        }
    }
}
