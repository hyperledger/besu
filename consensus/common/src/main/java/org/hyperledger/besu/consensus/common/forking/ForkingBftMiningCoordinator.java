/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.consensus.common.forking;

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.consensus.common.bft.blockcreation.BftMiningCoordinator;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class ForkingBftMiningCoordinator implements MiningCoordinator, BlockAddedObserver {
  private static final Logger LOG = getLogger();

  private final Map<Long, BftMiningCoordinator> miningCoordinatorForks;
  private BftMiningCoordinator activeMiningCoordinator;

  public ForkingBftMiningCoordinator(final Map<Long, BftMiningCoordinator> miningCoordinatorForks) {
    this.miningCoordinatorForks = miningCoordinatorForks;
    this.activeMiningCoordinator = miningCoordinatorForks.get(0L);
  }

  @Override
  public void start() {
    activeMiningCoordinator.start();
  }

  @Override
  public void stop() {
    activeMiningCoordinator.stop();
  }

  @Override
  public void awaitStop() throws InterruptedException {
    activeMiningCoordinator.awaitStop();
  }

  @Override
  public boolean enable() {
    return true;
  }

  @Override
  public boolean disable() {
    return false;
  }

  @Override
  public boolean isMining() {
    return true;
  }

  @Override
  public Wei getMinTransactionGasPrice() {
    return activeMiningCoordinator.getMinTransactionGasPrice();
  }

  @Override
  public void setExtraData(final Bytes extraData) {
    activeMiningCoordinator.setExtraData(extraData);
  }

  @Override
  public Optional<Address> getCoinbase() {
    return activeMiningCoordinator.getCoinbase();
  }

  @Override
  public Optional<Block> createBlock(
      final BlockHeader parentHeader,
      final List<Transaction> transactions,
      final List<BlockHeader> ommers) {
    return activeMiningCoordinator.createBlock(parentHeader, transactions, ommers);
  }

  @Override
  public void changeTargetGasLimit(final Long targetGasLimit) {
    activeMiningCoordinator.changeTargetGasLimit(targetGasLimit);
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event) {
    if (event.isNewCanonicalHead()) {
      final long nextBlock = event.getBlock().getHeader().getNumber() + 1;
      if (miningCoordinatorForks.containsKey(nextBlock)) {
        final BftMiningCoordinator newMiningCoordinator = miningCoordinatorForks.get(nextBlock);
        LOG.debug(
            "Switching mining coordinator at block {} from {} to {}",
            event.getBlock().getHeader().getNumber(),
            activeMiningCoordinator.getClass().getSimpleName(),
            newMiningCoordinator.getClass().getSimpleName());

        activeMiningCoordinator.stop();
        newMiningCoordinator.start();
        activeMiningCoordinator = newMiningCoordinator;
      }
      LOG.trace("New canonical head detected");
      activeMiningCoordinator.onBlockAdded(event);
    }
  }
}
