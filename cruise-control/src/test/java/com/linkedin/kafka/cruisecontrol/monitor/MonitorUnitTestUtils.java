/*
 * Copyright 2019 LinkedIn Corp. Licensed under the BSD 2-Clause License (the "License"). See License in the project root for license information.
 */

package com.linkedin.kafka.cruisecontrol.monitor;

import com.linkedin.kafka.cruisecontrol.KafkaCruiseControlUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.kafka.clients.Metadata;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.internals.ClusterResourceListeners;
import org.apache.kafka.common.protocol.Errors;
import org.apache.kafka.common.record.RecordBatch;
import org.apache.kafka.common.requests.MetadataResponse;
import org.apache.kafka.common.utils.LogContext;


public class MonitorUnitTestUtils {
  public static final long METADATA_REFRESH_BACKOFF = 10L;
  public static final long METADATA_EXPIRY_MS = 10L;
  public static final Node NODE_0 = new Node(0, "localhost", 100, "rack0");
  public static final Node NODE_1 = new Node(1, "localhost", 100, "rack1");
  private static final Node[] NODES = {NODE_0, NODE_1};

  private MonitorUnitTestUtils() {
  }

  /**
   * Get the clone of the nodes in the {@link #getCluster(Collection)} and {@link #getMetadata(Collection)}.
   */
  public static Node[] nodes() {
    return NODES.clone();
  }

  /**
   * Get metadata for the cluster generated by {@link #getCluster(Collection)} using the given partitions.
   *
   * @param partitions Partitions to include in the metadata.
   * @return metadata for the cluster generated by {@link #getCluster(Collection)} using the given partitions.
   */
  public static Metadata getMetadata(Collection<TopicPartition> partitions) {
    Cluster cluster = getCluster(partitions);

    Map<String, Set<TopicPartition>> topicToTopicPartitions = new HashMap<>(partitions.size());
    for (TopicPartition tp : partitions) {
      topicToTopicPartitions.putIfAbsent(tp.topic(), new HashSet<>());
      topicToTopicPartitions.get(tp.topic()).add(tp);
    }

    Metadata metadata = new Metadata(METADATA_REFRESH_BACKOFF,
                                     METADATA_EXPIRY_MS,
                                     new LogContext(),
                                     new ClusterResourceListeners());
    List<MetadataResponse.TopicMetadata> topicMetadata = new ArrayList<>(partitions.size());
    for (Map.Entry<String, Set<TopicPartition>> entry : topicToTopicPartitions.entrySet()) {
      List<MetadataResponse.PartitionMetadata> partitionMetadata = new ArrayList<>(entry.getValue().size());
      for (TopicPartition tp : entry.getValue()) {
        partitionMetadata.add(new MetadataResponse.PartitionMetadata(Errors.NONE, tp.partition(), cluster.leaderFor(tp),
                                                                     Optional.of(RecordBatch.NO_PARTITION_LEADER_EPOCH),
                                                                     Arrays.asList(NODES), Arrays.asList(NODES),
                                                                     Collections.emptyList()));
      }
      topicMetadata.add(new MetadataResponse.TopicMetadata(Errors.NONE, entry.getKey(), false, partitionMetadata));
    }

    MetadataResponse metadataResponse = KafkaCruiseControlUtils.prepareMetadataResponse(cluster.nodes(),
                                                                                        cluster.clusterResource().clusterId(),
                                                                                        MetadataResponse.NO_CONTROLLER_ID,
                                                                                        topicMetadata);
    metadata.update(KafkaCruiseControlUtils.REQUEST_VERSION_UPDATE, metadataResponse, 0);
    return metadata;
  }

  /**
   * Get cluster that consists of {@link #NODE_0} and {@link #NODE_1} to be used in tests.
   * Partitions to be queried all have leader replica at {@link #NODE_0} and replicas/ISR at {@link #NODES}.
   *
   * @param partitions Partitions to include in the cluster.
   * @return cluster that consists of {@link #NODE_0} and {@link #NODE_1} to be used in tests.
   */
  public static Cluster getCluster(Collection<TopicPartition> partitions) {
    Set<Node> allNodes = new HashSet<>(2);
    allNodes.add(NODE_0);
    allNodes.add(NODE_1);
    Set<PartitionInfo> partitionInfo = new HashSet<>(partitions.size());
    for (TopicPartition tp : partitions) {
      partitionInfo.add(new PartitionInfo(tp.topic(), tp.partition(), NODE_0, NODES, NODES));
    }
    return new Cluster("cluster_id", allNodes, partitionInfo, Collections.emptySet(), Collections.emptySet());
  }
}