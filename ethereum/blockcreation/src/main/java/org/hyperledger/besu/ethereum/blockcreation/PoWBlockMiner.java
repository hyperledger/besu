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

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.PoWSolution;
import org.hyperledger.besu.ethereum.mainnet.PoWSolverInputs;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.util.Subscribers;

import java.util.Optional;
import java.util.function.Function;

/**
 * Provides the proof-of-work specific aspects of the mining operation - i.e. getting the work
 * definition, reporting the hashrate of the miner and accepting work submissions.
 *
 * <p>All other aspects of mining (i.e. pre-block delays, block creation and importing to the chain)
 * are all conducted by the parent class.
 */
public class PoWBlockMiner extends BlockMiner<PoWBlockCreator> {

  /**
   * Instantiates a new Po w block miner.
   *
   * @param blockCreator the block creator
   * @param protocolSchedule the protocol schedule
   * @param protocolContext the protocol context
   * @param observers the observers
   * @param scheduler the scheduler
   * @param parentHeader the parent header
   */
  public PoWBlockMiner(
      final Function<BlockHeader, PoWBlockCreator> blockCreator,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final Subscribers<MinedBlockObserver> observers,
      final AbstractBlockScheduler scheduler,
      final BlockHeader parentHeader) {
    super(blockCreator, protocolSchedule, protocolContext, observers, scheduler, parentHeader);
  }

  /**
   * Gets work definition.
   *
   * @return the work definition
   */
  public Optional<PoWSolverInputs> getWorkDefinition() {
    return minerBlockCreator.getWorkDefinition();
  }

  /**
   * Gets hashes per second.
   *
   * @return the hashes per second
   */
  public Optional<Long> getHashesPerSecond() {
    return minerBlockCreator.getHashesPerSecond();
  }

  /**
   * Submit work boolean.
   *
   * @param solution the solution
   * @return the boolean
   */
  public boolean submitWork(final PoWSolution solution) {
    return minerBlockCreator.submitWork(solution);
  }
}
