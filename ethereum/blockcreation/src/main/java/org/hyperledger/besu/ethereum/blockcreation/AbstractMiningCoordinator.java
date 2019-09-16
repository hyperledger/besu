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
package org.hyperledger.besu.ethereum.blockcreation;

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.util.Subscribers;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.Logger;

public abstract class AbstractMiningCoordinator<
        C, M extends BlockMiner<C, ? extends AbstractBlockCreator<C>>>
    implements BlockAddedObserver, MiningCoordinator {

  private static final Logger LOG = getLogger();
  protected boolean isEnabled = false;
  protected volatile Optional<M> currentRunningMiner = Optional.empty();

  private final Subscribers<MinedBlockObserver> minedBlockObservers = Subscribers.create();
  private final AbstractMinerExecutor<C, M> executor;
  protected final Blockchain blockchain;
  private final SyncState syncState;

  public AbstractMiningCoordinator(
      final Blockchain blockchain,
      final AbstractMinerExecutor<C, M> executor,
      final SyncState syncState) {
    this.executor = executor;
    this.blockchain = blockchain;
    this.syncState = syncState;
    this.blockchain.observeBlockAdded(this);
    syncState.addInSyncListener(this::inSyncChanged);
  }

  @Override
  public Optional<Block> createBlock(
      final BlockHeader parentHeader,
      final List<Transaction> transactions,
      final List<BlockHeader> ommers) {
    M miner = executor.createMiner(parentHeader);
    return Optional.of(miner.createBlock(parentHeader, transactions, ommers));
  }

  @Override
  public void enable() {
    synchronized (this) {
      if (isEnabled) {
        return;
      }
      if (syncState.isInSync()) {
        startAsyncMiningOperation();
      }
      isEnabled = true;
    }
  }

  @Override
  public void disable() {
    synchronized (this) {
      if (!isEnabled) {
        return;
      }
      haltCurrentMiningOperation();
      isEnabled = false;
    }
  }

  @Override
  public boolean isRunning() {
    synchronized (this) {
      return currentRunningMiner.isPresent();
    }
  }

  protected void startAsyncMiningOperation() {
    final BlockHeader parentHeader = blockchain.getChainHeadHeader();
    currentRunningMiner = Optional.of(executor.startAsyncMining(minedBlockObservers, parentHeader));
  }

  protected void haltCurrentMiningOperation() {
    currentRunningMiner.ifPresent(M::cancel);
    currentRunningMiner = Optional.empty();
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event, final Blockchain blockchain) {
    synchronized (this) {
      if (isEnabled
          && event.isNewCanonicalHead()
          && newChainHeadInvalidatesMiningOperation(event.getBlock().getHeader())) {
        haltCurrentMiningOperation();
        if (syncState.isInSync()) {
          startAsyncMiningOperation();
        }
      }
    }
  }

  public void inSyncChanged(final boolean inSync) {
    synchronized (this) {
      if (isEnabled && inSync) {
        LOG.info("Resuming mining operations");
        startAsyncMiningOperation();
      } else if (!inSync) {
        if (isEnabled) {
          LOG.info("Pausing mining while behind chain head");
        }
        haltCurrentMiningOperation();
      }
    }
  }

  public void addMinedBlockObserver(final MinedBlockObserver obs) {
    minedBlockObservers.subscribe(obs);
  }

  @Override
  public Wei getMinTransactionGasPrice() {
    return executor.getMinTransactionGasPrice();
  }

  @Override
  public void setExtraData(final BytesValue extraData) {
    executor.setExtraData(extraData);
  }

  @Override
  public Optional<Address> getCoinbase() {
    return executor.getCoinbase();
  }

  protected abstract boolean newChainHeadInvalidatesMiningOperation(
      final BlockHeader newChainHeadHeader);
}
