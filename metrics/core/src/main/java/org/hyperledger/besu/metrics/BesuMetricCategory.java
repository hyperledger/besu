/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.metrics;

import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

public enum BesuMetricCategory implements MetricCategory {
  BLOCKCHAIN("blockchain"),
  ETHEREUM("ethereum", false),
  EXECUTORS("executors"),
  NETWORK("network"),
  PEERS("peers"),
  PERMISSIONING("permissioning"),
  KVSTORE_ROCKSDB("rocksdb"),
  KVSTORE_PRIVATE_ROCKSDB("private_rocksdb"),
  KVSTORE_ROCKSDB_STATS("rocksdb", false),
  KVSTORE_PRIVATE_ROCKSDB_STATS("private_rocksdb", false),
  PRUNER("pruner"),
  RPC("rpc"),
  SYNCHRONIZER("synchronizer"),
  TRANSACTION_POOL("transaction_pool"),
  STRATUM("stratum");

  private static final Optional<String> BESU_PREFIX = Optional.of("besu_");
  public static final Set<MetricCategory> DEFAULT_METRIC_CATEGORIES;

  static {
    // Why not KVSTORE_ROCKSDB and KVSTORE_ROCKSDB_STATS, KVSTORE_PRIVATE_ROCKSDB_STATS,
    // KVSTORE_PRIVATE_ROCKSDB_STATS?  They hurt performance under load.
    final EnumSet<BesuMetricCategory> besuCategories =
        EnumSet.complementOf(
            EnumSet.of(
                KVSTORE_ROCKSDB,
                KVSTORE_ROCKSDB_STATS,
                KVSTORE_PRIVATE_ROCKSDB,
                KVSTORE_PRIVATE_ROCKSDB_STATS));

    DEFAULT_METRIC_CATEGORIES =
        ImmutableSet.<MetricCategory>builder()
            .addAll(besuCategories)
            .addAll(EnumSet.allOf(StandardMetricCategory.class))
            .build();
  }

  private final String name;
  private final boolean besuSpecific;

  BesuMetricCategory(final String name) {
    this(name, true);
  }

  BesuMetricCategory(final String name, final boolean besuSpecific) {
    this.name = name;
    this.besuSpecific = besuSpecific;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Optional<String> getApplicationPrefix() {
    return besuSpecific ? BESU_PREFIX : Optional.empty();
  }
}
