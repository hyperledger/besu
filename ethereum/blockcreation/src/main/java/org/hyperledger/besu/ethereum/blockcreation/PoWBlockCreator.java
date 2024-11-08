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
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.SealableBlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.EthHash;
import org.hyperledger.besu.ethereum.mainnet.PoWSolution;
import org.hyperledger.besu.ethereum.mainnet.PoWSolver;
import org.hyperledger.besu.ethereum.mainnet.PoWSolverInputs;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.apache.tuweni.units.bigints.UInt256;

public class PoWBlockCreator extends AbstractBlockCreator {

  private final PoWSolver nonceSolver;

  public PoWBlockCreator(
      final MiningConfiguration miningConfiguration,
      final ExtraDataCalculator extraDataCalculator,
      final TransactionPool transactionPool,
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final PoWSolver nonceSolver,
      final EthScheduler ethScheduler) {
    super(
        miningConfiguration,
        __ -> miningConfiguration.getCoinbase().orElseThrow(),
        extraDataCalculator,
        transactionPool,
        protocolContext,
        protocolSchedule,
        ethScheduler);

    this.nonceSolver = nonceSolver;
  }

  @Override
  protected BlockHeader createFinalBlockHeader(final SealableBlockHeader sealableBlockHeader) {
    final PoWSolverInputs workDefinition = generateNonceSolverInputs(sealableBlockHeader);
    final PoWSolution solution;
    try {
      solution = nonceSolver.solveFor(PoWSolver.PoWSolverJob.createFromInputs(workDefinition));
    } catch (final InterruptedException ex) {
      throw new CancellationException();
    } catch (final ExecutionException ex) {
      throw new RuntimeException("Failure occurred during nonce calculations.", ex);
    }
    return BlockHeaderBuilder.create()
        .populateFrom(sealableBlockHeader)
        .mixHash(solution.getMixHash())
        .nonce(solution.getNonce())
        .blockHeaderFunctions(blockHeaderFunctions)
        .buildBlockHeader();
  }

  private PoWSolverInputs generateNonceSolverInputs(final SealableBlockHeader sealableBlockHeader) {
    final BigInteger difficulty = sealableBlockHeader.getDifficulty().toBigInteger();
    final UInt256 target =
        difficulty.equals(BigInteger.ONE)
            ? UInt256.MAX_VALUE
            : UInt256.valueOf(EthHash.TARGET_UPPER_BOUND.divide(difficulty));

    return new PoWSolverInputs(
        target, EthHash.hashHeader(sealableBlockHeader), sealableBlockHeader.getNumber());
  }

  public Optional<PoWSolverInputs> getWorkDefinition() {
    return nonceSolver.getWorkDefinition();
  }

  public Optional<Long> getHashesPerSecond() {
    return nonceSolver.hashesPerSecond();
  }

  public boolean submitWork(final PoWSolution solution) {
    return nonceSolver.submitSolution(solution);
  }

  @Override
  public void cancel() {
    super.cancel();
    nonceSolver.cancel();
  }
}
