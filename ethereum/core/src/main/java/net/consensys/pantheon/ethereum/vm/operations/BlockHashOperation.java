package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.ProcessableBlockHeader;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.uint.UInt256;

import java.util.Optional;

public class BlockHashOperation extends AbstractOperation {
  private static final int MAX_RELATIVE_BLOCK = 255;

  public BlockHashOperation(final GasCalculator gasCalculator) {
    super(0x40, "BLOCKHASH", 1, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getBlockHashOperationGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 blockArg = frame.popStackItem().asUInt256();

    // Short-circuit if value is unreasonably large
    if (!blockArg.fitsLong()) {
      frame.pushStackItem(Bytes32.ZERO);
      return;
    }

    final long soughtBlock = blockArg.toLong();
    final ProcessableBlockHeader blockHeader = frame.getBlockHeader();
    final long currentBlockNumber = blockHeader.getNumber();
    final long mostRecentBlockNumber = currentBlockNumber - 1;

    // If the current block is the genesis block or the sought block is
    // not within the last 256 completed blocks, zero is returned.
    if (currentBlockNumber == 0
        || soughtBlock < (mostRecentBlockNumber - MAX_RELATIVE_BLOCK)
        || soughtBlock > mostRecentBlockNumber) {
      frame.pushStackItem(Bytes32.ZERO);
    } else {
      final Optional<Hash> maybeBlockHash = frame.getBlockchain().getBlockHashByNumber(soughtBlock);
      assert maybeBlockHash.isPresent();
      frame.pushStackItem(maybeBlockHash.get());
    }
  }
}
