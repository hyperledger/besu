/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.metrics;

import tech.pegasys.pantheon.plugin.services.metrics.MetricCategory;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

public enum PantheonMetricCategory implements MetricCategory {
  BLOCKCHAIN("blockchain"),
  ETHEREUM("ethereum", false),
  EXECUTORS("executors"),
  NETWORK("network"),
  PEERS("peers"),
  PERMISSIONING("permissioning"),
  KVSTORE_ROCKSDB("rocksdb"),
  KVSTORE_ROCKSDB_STATS("rocksdb", false),
  PRUNER("pruner"),
  RPC("rpc"),
  SYNCHRONIZER("synchronizer"),
  TRANSACTION_POOL("transaction_pool");

  private static final Optional<String> PANTHEON_PREFIX = Optional.of("pantheon_");
  public static final Set<MetricCategory> DEFAULT_METRIC_CATEGORIES;

  static {
    // Why not ROCKSDB and KVSTORE_ROCKSDB_STATS?  They hurt performance under load.
    final EnumSet<PantheonMetricCategory> pantheonCategories =
        EnumSet.complementOf(EnumSet.of(KVSTORE_ROCKSDB, KVSTORE_ROCKSDB_STATS));

    DEFAULT_METRIC_CATEGORIES =
        ImmutableSet.<MetricCategory>builder()
            .addAll(pantheonCategories)
            .addAll(EnumSet.allOf(StandardMetricCategory.class))
            .build();
  }

  private final String name;
  private final boolean pantheonSpecific;

  PantheonMetricCategory(final String name) {
    this(name, true);
  }

  PantheonMetricCategory(final String name, final boolean pantheonSpecific) {
    this.name = name;
    this.pantheonSpecific = pantheonSpecific;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Optional<String> getApplicationPrefix() {
    return pantheonSpecific ? PANTHEON_PREFIX : Optional.empty();
  }
}
