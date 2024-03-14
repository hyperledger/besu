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
package org.hyperledger.besu.consensus.clique.blockcreation;

import org.hyperledger.besu.config.CliqueConfigOptions;
import org.hyperledger.besu.consensus.clique.CliqueHelpers;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.AbstractBlockScheduler;
import org.hyperledger.besu.ethereum.blockcreation.BlockMiner;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.util.Subscribers;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Clique block miner. */
public class CliqueBlockMiner extends BlockMiner<CliqueBlockCreator> {
  private static final Logger LOG = LoggerFactory.getLogger(CliqueBlockMiner.class);
  private static final int WAIT_IN_MS_BETWEEN_EMPTY_BUILD_ATTEMPTS = 1_000;

  private final Address localAddress;
  private final ForksSchedule<CliqueConfigOptions> forksSchedule;

  /**
   * Instantiates a new Clique block miner.
   *
   * @param blockCreator the block creator
   * @param protocolSchedule the protocol schedule
   * @param protocolContext the protocol context
   * @param observers the observers
   * @param scheduler the scheduler
   * @param parentHeader the parent header
   * @param localAddress the local address
   * @param forksSchedule the transitions
   */
  public CliqueBlockMiner(
      final Function<BlockHeader, CliqueBlockCreator> blockCreator,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final Subscribers<MinedBlockObserver> observers,
      final AbstractBlockScheduler scheduler,
      final BlockHeader parentHeader,
      final Address localAddress,
      final ForksSchedule<CliqueConfigOptions> forksSchedule) {
    super(blockCreator, protocolSchedule, protocolContext, observers, scheduler, parentHeader);
    this.localAddress = localAddress;
    this.forksSchedule = forksSchedule;
  }

  @Override
  protected boolean mineBlock() throws InterruptedException {
    if (CliqueHelpers.addressIsAllowedToProduceNextBlock(
        localAddress, protocolContext, parentHeader)) {
      return super.mineBlock();
    }

    return true; // terminate mining.
  }

  @Override
  protected boolean shouldImportBlock(final Block block) throws InterruptedException {
    if (forksSchedule.getFork(block.getHeader().getNumber()).getValue().getCreateEmptyBlocks()) {
      return true;
    }

    final boolean isEmpty = block.getBody().getTransactions().isEmpty();
    if (isEmpty) {
      LOG.debug("Skipping creating empty block {}", block.toLogString());
      Thread.sleep(WAIT_IN_MS_BETWEEN_EMPTY_BUILD_ATTEMPTS);
    }
    return !isEmpty;
  }
}
