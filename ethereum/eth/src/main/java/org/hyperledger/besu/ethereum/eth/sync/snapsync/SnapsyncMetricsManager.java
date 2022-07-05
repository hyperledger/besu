/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import static io.netty.util.internal.ObjectUtil.checkNonEmpty;

import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapsyncMetricsManager {

  private static final Logger LOG = LoggerFactory.getLogger(SnapsyncMetricsManager.class);
  private static final long PRINT_DELAY = TimeUnit.MINUTES.toMillis(1);

  private final MetricsSystem metricsSystem;

  private final AtomicReference<BigDecimal> percentageDownloaded;
  private final AtomicLong nbAccounts;
  private final AtomicLong nbSlots;
  private final AtomicLong nbCodes;
  private final AtomicLong nbNodesGenerated;
  private final AtomicLong nbNodesHealed;
  private long startSyncTime;

  private final Map<Bytes32, BigInteger> lastRangeIndex = new HashMap<>();

  private long lastNotifyTimestamp;

  public SnapsyncMetricsManager(final MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    percentageDownloaded = new AtomicReference<>(new BigDecimal(0));
    nbAccounts = new AtomicLong(0);
    nbSlots = new AtomicLong(0);
    nbCodes = new AtomicLong(0);
    nbNodesGenerated = new AtomicLong(0);
    nbNodesHealed = new AtomicLong(0);

    metricsSystem.createLongGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "snap_world_state_generated_nodes_total",
        "Total number of data nodes generated as part of snap sync world state download",
        nbNodesGenerated::get);
    metricsSystem.createLongGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "snap_world_state_healed_nodes_total",
        "Total number of data nodes healed as part of snap sync world state heal process",
        nbNodesHealed::get);
    metricsSystem.createLongGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "snap_world_state_accounts_total",
        "Total number of accounts downloaded as part of snap sync world state",
        nbAccounts::get);
    metricsSystem.createLongGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "snap_world_state_slots_total",
        "Total number of slots downloaded as part of snap sync world state",
        nbSlots::get);
    metricsSystem.createLongGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "snap_world_state_codes_total",
        "Total number of codes downloaded as part of snap sync world state",
        nbCodes::get);
  }

  public void initRange(final Map<Bytes32, Bytes32> ranges) {
    for (Map.Entry<Bytes32, Bytes32> entry : ranges.entrySet()) {
      lastRangeIndex.put(entry.getValue(), entry.getKey().toUnsignedBigInteger());
    }
    startSyncTime = System.currentTimeMillis();
    lastNotifyTimestamp = startSyncTime;
  }

  public void notifyStateDownloaded(final Bytes32 startKeyHash, final Bytes32 endKeyHash) {
    checkNonEmpty(lastRangeIndex, "snapsync range collection");
    if (lastRangeIndex.containsKey(endKeyHash)) {
      final BigInteger lastPos = lastRangeIndex.get(endKeyHash);
      final BigInteger newPos = startKeyHash.toUnsignedBigInteger();
      percentageDownloaded.getAndAccumulate(
          BigDecimal.valueOf(100)
              .multiply(new BigDecimal(newPos.subtract(lastPos)))
              .divide(
                  new BigDecimal(RangeManager.MAX_RANGE.toUnsignedBigInteger()),
                  MathContext.DECIMAL32),
          BigDecimal::add);
      lastRangeIndex.put(endKeyHash, newPos);
      print(false);
    }
  }

  public void notifyAccountsDownloaded(final long nbAccounts) {
    this.nbAccounts.getAndAdd(nbAccounts);
  }

  public void notifySlotsDownloaded(final long nbSlots) {
    this.nbSlots.getAndAdd(nbSlots);
  }

  public void notifyCodeDownloaded() {
    this.nbCodes.getAndIncrement();
  }

  public void notifyNodesGenerated(final long nbNodes) {
    this.nbNodesGenerated.getAndAdd(nbNodes);
  }

  public void notifyNodesHealed(final long nbNodes) {
    this.nbNodesHealed.getAndAdd(nbNodes);
    print(true);
  }

  private void print(final boolean isHeal) {
    final long now = System.currentTimeMillis();
    if (now - lastNotifyTimestamp >= PRINT_DELAY) {
      lastNotifyTimestamp = now;
      if (!isHeal) {
        LOG.info(
            "Worldstate download in progress synced={}%, accounts={}, slots={}, codes={}, nodes={}",
            percentageDownloaded.get().setScale(2, RoundingMode.HALF_UP),
            nbAccounts,
            nbSlots,
            nbCodes,
            nbNodesGenerated);
      } else {
        LOG.info("Healed {} world state nodes", nbNodesHealed.get());
      }
    }
  }

  public void notifySnapSyncCompleted() {
    final Duration duration = Duration.ofMillis(System.currentTimeMillis() - startSyncTime);
    LOG.info(
        "Finished snapsync with nodes {} (healed={}) duration {}{}:{},{}",
        nbNodesGenerated.addAndGet(nbNodesHealed.get()),
        nbNodesHealed,
        duration.toHoursPart() > 0 ? (duration.toHoursPart() + ":") : "",
        duration.toMinutesPart(),
        duration.toSecondsPart(),
        duration.toMillisPart());
  }

  public MetricsSystem getMetricsSystem() {
    return metricsSystem;
  }
}
