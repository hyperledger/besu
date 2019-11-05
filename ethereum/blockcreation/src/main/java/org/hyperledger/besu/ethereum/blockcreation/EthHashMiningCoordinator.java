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

import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.EthHashSolution;
import org.hyperledger.besu.ethereum.mainnet.EthHashSolverInputs;

import java.util.Optional;

/**
 * Responsible for determining when a block mining operation should be started/stopped, then
 * creating an appropriate miner and starting it running in a thread.
 */
public class EthHashMiningCoordinator extends AbstractMiningCoordinator<Void, EthHashBlockMiner>
    implements BlockAddedObserver {

  private final EthHashMinerExecutor executor;
  private volatile Optional<Long> cachedHashesPerSecond = Optional.empty();

  public EthHashMiningCoordinator(
      final Blockchain blockchain, final EthHashMinerExecutor executor, final SyncState syncState) {
    super(blockchain, executor, syncState);
    this.executor = executor;
  }

  @Override
  public void setCoinbase(final Address coinbase) {
    executor.setCoinbase(coinbase);
  }

  public void setStratumMiningEnabled(final boolean stratumMiningEnabled) {
    executor.setStratumMiningEnabled(stratumMiningEnabled);
  }

  @Override
  public Optional<Long> hashesPerSecond() {
    final Optional<Long> currentHashesPerSecond =
        currentRunningMiner.flatMap(EthHashBlockMiner::getHashesPerSecond);

    if (currentHashesPerSecond.isPresent()) {
      cachedHashesPerSecond = currentHashesPerSecond;
      return currentHashesPerSecond;
    } else {
      return cachedHashesPerSecond;
    }
  }

  @Override
  public Optional<EthHashSolverInputs> getWorkDefinition() {
    return currentRunningMiner.flatMap(EthHashBlockMiner::getWorkDefinition);
  }

  @Override
  public boolean submitWork(final EthHashSolution solution) {
    synchronized (this) {
      return currentRunningMiner.map(miner -> miner.submitWork(solution)).orElse(false);
    }
  }

  @Override
  protected void haltMiner(final EthHashBlockMiner miner) {
    miner.cancel();
    miner.getHashesPerSecond().ifPresent(val -> cachedHashesPerSecond = Optional.of(val));
  }

  @Override
  protected boolean newChainHeadInvalidatesMiningOperation(final BlockHeader newChainHeadHeader) {
    return true;
  }
}
