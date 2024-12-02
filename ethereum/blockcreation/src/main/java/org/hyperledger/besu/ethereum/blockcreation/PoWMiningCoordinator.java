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
package org.hyperledger.besu.ethereum.blockcreation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator;
import org.hyperledger.besu.ethereum.mainnet.PoWSolution;
import org.hyperledger.besu.ethereum.mainnet.PoWSolverInputs;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for determining when a block mining operation should be started/stopped, then
 * creating an appropriate miner and starting it running in a thread.
 */
@Deprecated(since = "24.12.0")
public class PoWMiningCoordinator extends AbstractMiningCoordinator<PoWBlockMiner>
    implements BlockAddedObserver {

  private static final Logger LOG = LoggerFactory.getLogger(PoWMiningCoordinator.class);

  private final PoWMinerExecutor executor;

  private final Cache<String, Long> sealerHashRate;

  private volatile Optional<Long> cachedHashesPerSecond = Optional.empty();

  public PoWMiningCoordinator(
      final Blockchain blockchain,
      final PoWMinerExecutor executor,
      final SyncState syncState,
      final int remoteSealersLimit,
      final long remoteSealersTimeToLive) {
    super(blockchain, executor, syncState);
    this.executor = executor;
    this.sealerHashRate =
        CacheBuilder.newBuilder()
            .maximumSize(remoteSealersLimit)
            .expireAfterWrite(remoteSealersTimeToLive, TimeUnit.MINUTES)
            .build();
  }

  @Override
  public void setCoinbase(final Address coinbase) {
    executor.setCoinbase(coinbase);
  }

  public void setStratumMiningEnabled(final boolean stratumMiningEnabled) {
    executor.setStratumMiningEnabled(stratumMiningEnabled);
  }

  @Override
  public void onResumeMining() {
    LOG.info("Resuming mining operations");
  }

  @Override
  public void onPauseMining() {
    LOG.info("Pausing mining while behind chain head");
  }

  @Override
  public Optional<Long> hashesPerSecond() {
    if (sealerHashRate.size() <= 0) {
      return localHashesPerSecond();
    } else {
      return remoteHashesPerSecond();
    }
  }

  private Optional<Long> remoteHashesPerSecond() {
    return Optional.of(sealerHashRate.asMap().values().stream().mapToLong(Long::longValue).sum());
  }

  private Optional<Long> localHashesPerSecond() {
    final Optional<Long> currentHashesPerSecond =
        currentRunningMiner.flatMap(PoWBlockMiner::getHashesPerSecond);

    if (currentHashesPerSecond.isPresent()) {
      cachedHashesPerSecond = currentHashesPerSecond;
      return currentHashesPerSecond;
    } else {
      return cachedHashesPerSecond;
    }
  }

  @Override
  public boolean submitHashRate(final String id, final Long hashrate) {
    if (hashrate == 0) {
      return false;
    }
    LOG.info("Hashrate submitted id {} hashrate {}", id, hashrate);
    sealerHashRate.put(id, hashrate);
    return true;
  }

  @Override
  public void changeTargetGasLimit(final Long targetGasLimit) {
    executor.changeTargetGasLimit(targetGasLimit);
  }

  @Override
  public Optional<PoWSolverInputs> getWorkDefinition() {
    return currentRunningMiner.flatMap(PoWBlockMiner::getWorkDefinition);
  }

  @Override
  public boolean submitWork(final PoWSolution solution) {
    synchronized (this) {
      return currentRunningMiner.map(miner -> miner.submitWork(solution)).orElse(false);
    }
  }

  @Override
  protected void haltMiner(final PoWBlockMiner miner) {
    miner.cancel();
    miner.getHashesPerSecond().ifPresent(val -> cachedHashesPerSecond = Optional.of(val));
  }

  @Override
  protected boolean newChainHeadInvalidatesMiningOperation(final BlockHeader newChainHeadHeader) {
    return true;
  }

  public EpochCalculator getEpochCalculator() {
    return executor.epochCalculator;
  }
}
