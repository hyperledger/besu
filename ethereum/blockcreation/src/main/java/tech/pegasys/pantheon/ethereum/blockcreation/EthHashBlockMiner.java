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

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MinedBlockObserver;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashSolution;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashSolverInputs;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.util.Subscribers;

import java.util.Optional;
import java.util.function.Function;

/**
 * Provides the EthHash specific aspects of the mining operation - i.e. getting the work definition,
 * reporting the hashrate of the miner and accepting work submissions.
 *
 * <p>All other aspects of mining (i.e. pre-block delays, block creation and importing to the chain)
 * are all conducted by the parent class.
 */
public class EthHashBlockMiner extends BlockMiner<Void, EthHashBlockCreator> {

  public EthHashBlockMiner(
      final Function<BlockHeader, EthHashBlockCreator> blockCreator,
      final ProtocolSchedule<Void> protocolSchedule,
      final ProtocolContext<Void> protocolContext,
      final Subscribers<MinedBlockObserver> observers,
      final AbstractBlockScheduler scheduler,
      final BlockHeader parentHeader) {
    super(blockCreator, protocolSchedule, protocolContext, observers, scheduler, parentHeader);
  }

  public Optional<EthHashSolverInputs> getWorkDefinition() {
    return minerBlockCreator.getWorkDefinition();
  }

  public Optional<Long> getHashesPerSecond() {
    return minerBlockCreator.getHashesPerSecond();
  }

  public boolean submitWork(final EthHashSolution solution) {
    return minerBlockCreator.submitWork(solution);
  }
}
