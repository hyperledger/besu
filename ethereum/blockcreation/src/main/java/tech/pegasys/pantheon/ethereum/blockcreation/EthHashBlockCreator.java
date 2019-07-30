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
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderBuilder;
import tech.pegasys.pantheon.ethereum.core.SealableBlockHeader;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.mainnet.EthHash;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashSolution;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashSolver;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashSolver.EthHashSolverJob;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashSolverInputs;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.util.bytes.BytesValues;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public class EthHashBlockCreator extends AbstractBlockCreator<Void> {

  private final EthHashSolver nonceSolver;

  public EthHashBlockCreator(
      final Address coinbase,
      final ExtraDataCalculator extraDataCalculator,
      final PendingTransactions pendingTransactions,
      final ProtocolContext<Void> protocolContext,
      final ProtocolSchedule<Void> protocolSchedule,
      final Function<Long, Long> gasLimitCalculator,
      final EthHashSolver nonceSolver,
      final Wei minTransactionGasPrice,
      final BlockHeader parentHeader) {
    super(
        coinbase,
        extraDataCalculator,
        pendingTransactions,
        protocolContext,
        protocolSchedule,
        gasLimitCalculator,
        minTransactionGasPrice,
        coinbase,
        parentHeader);

    this.nonceSolver = nonceSolver;
  }

  @Override
  protected BlockHeader createFinalBlockHeader(final SealableBlockHeader sealableBlockHeader) {
    final EthHashSolverInputs workDefinition = generateNonceSolverInputs(sealableBlockHeader);
    final EthHashSolution solution;
    try {
      solution = nonceSolver.solveFor(EthHashSolverJob.createFromInputs(workDefinition));
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

  private EthHashSolverInputs generateNonceSolverInputs(
      final SealableBlockHeader sealableBlockHeader) {
    final BigInteger difficulty =
        BytesValues.asUnsignedBigInteger(sealableBlockHeader.getDifficulty().getBytes());
    final UInt256 target =
        difficulty.equals(BigInteger.ONE)
            ? UInt256.MAX_VALUE
            : UInt256.of(EthHash.TARGET_UPPER_BOUND.divide(difficulty));

    return new EthHashSolverInputs(
        target, EthHash.hashHeader(sealableBlockHeader), sealableBlockHeader.getNumber());
  }

  public Optional<EthHashSolverInputs> getWorkDefinition() {
    return nonceSolver.getWorkDefinition();
  }

  public Optional<Long> getHashesPerSecond() {
    return nonceSolver.hashesPerSecond();
  }

  public boolean submitWork(final EthHashSolution solution) {
    return nonceSolver.submitSolution(solution);
  }

  @Override
  public void cancel() {
    super.cancel();
    nonceSolver.cancel();
  }
}
