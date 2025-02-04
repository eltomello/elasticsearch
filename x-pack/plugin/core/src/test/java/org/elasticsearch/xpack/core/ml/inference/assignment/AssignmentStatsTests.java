/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.ml.inference.assignment;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.test.AbstractWireSerializingTestCase;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.InferenceStats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;

public class AssignmentStatsTests extends AbstractWireSerializingTestCase<AssignmentStats> {

    public static AssignmentStats randomDeploymentStats() {
        List<AssignmentStats.NodeStats> nodeStatsList = new ArrayList<>();
        int numNodes = randomIntBetween(1, 4);
        for (int i = 0; i < numNodes; i++) {
            var node = new DiscoveryNode("node_" + i, buildNewFakeTransportAddress(), Version.CURRENT);
            if (randomBoolean()) {
                nodeStatsList.add(randomNodeStats(node));
            } else {
                nodeStatsList.add(
                    AssignmentStats.NodeStats.forNotStartedState(
                        node,
                        randomFrom(RoutingState.values()),
                        randomBoolean() ? null : "a good reason"
                    )
                );
            }
        }

        nodeStatsList.sort(Comparator.comparing(n -> n.getNode().getId()));

        return new AssignmentStats(
            randomAlphaOfLength(5),
            randomBoolean() ? null : randomIntBetween(1, 8),
            randomBoolean() ? null : randomIntBetween(1, 8),
            randomBoolean() ? null : randomIntBetween(1, 10000),
            Instant.now(),
            nodeStatsList
        );
    }

    public static AssignmentStats.NodeStats randomNodeStats(DiscoveryNode node) {
        var lastAccess = Instant.now();
        var inferenceCount = randomNonNegativeLong();
        Double avgInferenceTime = randomDoubleBetween(0.0, 100.0, true);
        Double avgInferenceTimeLastPeriod = randomDoubleBetween(0.0, 100.0, true);

        var noInferenceCallsOnNodeYet = randomBoolean();
        if (noInferenceCallsOnNodeYet) {
            lastAccess = null;
            inferenceCount = 0;
            avgInferenceTime = null;
            avgInferenceTimeLastPeriod = null;
        }
        return AssignmentStats.NodeStats.forStartedState(
            node,
            inferenceCount,
            avgInferenceTime,
            randomIntBetween(0, 100),
            randomIntBetween(0, 100),
            randomIntBetween(0, 100),
            randomIntBetween(0, 100),
            lastAccess,
            Instant.now(),
            randomIntBetween(1, 16),
            randomIntBetween(1, 16),
            randomIntBetween(0, 100),
            randomIntBetween(0, 100),
            avgInferenceTimeLastPeriod
        );
    }

    public void testGetOverallInferenceStats() {
        String modelId = randomAlphaOfLength(10);

        AssignmentStats existingStats = new AssignmentStats(
            modelId,
            randomBoolean() ? null : randomIntBetween(1, 8),
            randomBoolean() ? null : randomIntBetween(1, 8),
            randomBoolean() ? null : randomIntBetween(1, 10000),
            Instant.now(),
            List.of(
                AssignmentStats.NodeStats.forStartedState(
                    new DiscoveryNode("node_started_1", buildNewFakeTransportAddress(), Version.CURRENT),
                    10L,
                    randomDoubleBetween(0.0, 100.0, true),
                    randomIntBetween(1, 10),
                    5,
                    12,
                    3,
                    Instant.now(),
                    Instant.now(),
                    randomIntBetween(1, 2),
                    randomIntBetween(1, 2),
                    randomNonNegativeLong(),
                    randomNonNegativeLong(),
                    null
                ),
                AssignmentStats.NodeStats.forStartedState(
                    new DiscoveryNode("node_started_2", buildNewFakeTransportAddress(), Version.CURRENT),
                    12L,
                    randomDoubleBetween(0.0, 100.0, true),
                    randomIntBetween(1, 10),
                    15,
                    4,
                    2,
                    Instant.now(),
                    Instant.now(),
                    randomIntBetween(1, 2),
                    randomIntBetween(1, 2),
                    randomNonNegativeLong(),
                    randomNonNegativeLong(),
                    null
                ),
                AssignmentStats.NodeStats.forNotStartedState(
                    new DiscoveryNode("node_not_started_3", buildNewFakeTransportAddress(), Version.CURRENT),
                    randomFrom(RoutingState.values()),
                    randomBoolean() ? null : "a good reason"
                )
            )
        );
        InferenceStats stats = existingStats.getOverallInferenceStats();
        assertThat(stats.getModelId(), equalTo(modelId));
        assertThat(stats.getInferenceCount(), equalTo(22L));
        assertThat(stats.getFailureCount(), equalTo(41L));
    }

    public void testGetOverallInferenceStatsWithNoNodes() {
        String modelId = randomAlphaOfLength(10);

        AssignmentStats existingStats = new AssignmentStats(
            modelId,
            randomBoolean() ? null : randomIntBetween(1, 8),
            randomBoolean() ? null : randomIntBetween(1, 8),
            randomBoolean() ? null : randomIntBetween(1, 10000),
            Instant.now(),
            List.of()
        );
        InferenceStats stats = existingStats.getOverallInferenceStats();
        assertThat(stats.getModelId(), equalTo(modelId));
        assertThat(stats.getInferenceCount(), equalTo(0L));
        assertThat(stats.getFailureCount(), equalTo(0L));
    }

    public void testGetOverallInferenceStatsWithOnlyStoppedNodes() {
        String modelId = randomAlphaOfLength(10);

        AssignmentStats existingStats = new AssignmentStats(
            modelId,
            randomBoolean() ? null : randomIntBetween(1, 8),
            randomBoolean() ? null : randomIntBetween(1, 8),
            randomBoolean() ? null : randomIntBetween(1, 10000),
            Instant.now(),
            List.of(
                AssignmentStats.NodeStats.forNotStartedState(
                    new DiscoveryNode("node_not_started_1", buildNewFakeTransportAddress(), Version.CURRENT),
                    randomFrom(RoutingState.values()),
                    randomBoolean() ? null : "a good reason"
                ),
                AssignmentStats.NodeStats.forNotStartedState(
                    new DiscoveryNode("node_not_started_2", buildNewFakeTransportAddress(), Version.CURRENT),
                    randomFrom(RoutingState.values()),
                    randomBoolean() ? null : "a good reason"
                )
            )
        );
        InferenceStats stats = existingStats.getOverallInferenceStats();
        assertThat(stats.getModelId(), equalTo(modelId));
        assertThat(stats.getInferenceCount(), equalTo(0L));
        assertThat(stats.getFailureCount(), equalTo(0L));
    }

    @Override
    protected Writeable.Reader<AssignmentStats> instanceReader() {
        return AssignmentStats::new;
    }

    @Override
    protected AssignmentStats createTestInstance() {
        return randomDeploymentStats();
    }
}
