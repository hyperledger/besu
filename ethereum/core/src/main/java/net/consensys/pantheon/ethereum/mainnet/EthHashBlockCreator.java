package net.consensys.pantheon.ethereum.mainnet;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.AbstractBlockCreator;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockHeaderBuilder;
import net.consensys.pantheon.ethereum.core.PendingTransactions;
import net.consensys.pantheon.ethereum.core.SealableBlockHeader;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.mainnet.EthHashSolver.EthHashSolverJob;
import net.consensys.pantheon.util.bytes.BytesValues;
import net.consensys.pantheon.util.uint.UInt256;

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
    EthHashSolution solution;
    try {
      solution = nonceSolver.solveFor(EthHashSolverJob.createFromInputs(workDefinition));
    } catch (final InterruptedException ex) {
      throw new CancellationException();
    } catch (final ExecutionException ex) {
      throw new RuntimeException("Failure occurred during nonce calculations.");
    }
    return BlockHeaderBuilder.create()
        .populateFrom(sealableBlockHeader)
        .mixHash(solution.getMixHash())
        .nonce(solution.getNonce())
        .blockHashFunction(MainnetBlockHashFunction::createHash)
        .buildBlockHeader();
  }

  private EthHashSolverInputs generateNonceSolverInputs(
      final SealableBlockHeader sealableBlockHeader) {
    final BigInteger difficulty =
        BytesValues.asUnsignedBigInteger(sealableBlockHeader.getDifficulty().getBytes());
    final UInt256 target = UInt256.of(EthHash.TARGET_UPPER_BOUND.divide(difficulty));

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
