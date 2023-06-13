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

import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
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
  private final EthContext ethContext;

  private final AtomicReference<BigDecimal> percentageDownloaded;
  private final AtomicLong nbAccounts;
  private final AtomicLong nbSlots;
  private final AtomicLong nbCodes;
  private final AtomicLong nbNodesGenerated;
  private final AtomicLong nbNodesHealed;
  private long startSyncTime;

  private long lastNotifyTimestamp;

  public SnapsyncMetricsManager(final MetricsSystem metricsSystem, final EthContext ethContext) {
    this.metricsSystem = metricsSystem;
    this.ethContext = ethContext;
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

  public void initRange() {
    startSyncTime = System.currentTimeMillis();
    lastNotifyTimestamp = startSyncTime;
  }

  public void notifyStateDownloaded(final Bytes32 startKeyHash, final Bytes32 endKeyHash) {
    percentageDownloaded.getAndAccumulate(
        BigDecimal.valueOf(100)
            .multiply(
                new BigDecimal(endKeyHash.toUnsignedBigInteger())
                    .subtract(new BigDecimal(startKeyHash.toUnsignedBigInteger())))
            .divide(
                new BigDecimal(RangeManager.MAX_RANGE.toUnsignedBigInteger()),
                MathContext.DECIMAL32),
        BigDecimal::add);
    print(false);
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
        int peerCount = -1; // ethContext is not available in tests
        if (ethContext != null && ethContext.getEthPeers().peerCount() >= 0) {
          peerCount = ethContext.getEthPeers().peerCount();
        }
        LOG.debug(
            "Worldstate download in progress accounts={}, slots={}, codes={}, nodes={}",
            nbAccounts,
            nbSlots,
            nbCodes,
            nbNodesGenerated);
        LOG.info(
            "Worldstate download progress: {}%, Peer count: {}",
            percentageDownloaded.get().setScale(2, RoundingMode.HALF_UP), peerCount);
      } else {
        LOG.info("Healed {} world state nodes", nbNodesHealed.get());
      }
    }
  }

  public void notifySnapSyncCompleted() {
    final Duration duration = Duration.ofMillis(System.currentTimeMillis() - startSyncTime);
    LOG.info(
        "Finished worldstate snapsync with nodes {} (healed={}) duration {}{}:{},{}.",
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
