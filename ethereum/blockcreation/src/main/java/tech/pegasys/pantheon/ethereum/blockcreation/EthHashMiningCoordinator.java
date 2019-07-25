/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.blockcreation;

import tech.pegasys.pantheon.ethereum.chain.BlockAddedObserver;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashSolution;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashSolverInputs;

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
  protected void haltCurrentMiningOperation() {
    currentRunningMiner.ifPresent(
        miner -> {
          miner.cancel();
          miner.getHashesPerSecond().ifPresent(val -> cachedHashesPerSecond = Optional.of(val));
        });
    currentRunningMiner = Optional.empty();
  }

  @Override
  protected boolean newChainHeadInvalidatesMiningOperation(final BlockHeader newChainHeadHeader) {
    return true;
  }
}
