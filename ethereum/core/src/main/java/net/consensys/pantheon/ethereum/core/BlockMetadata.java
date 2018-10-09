package net.consensys.pantheon.ethereum.core;

import net.consensys.pantheon.ethereum.rlp.RLP;
import net.consensys.pantheon.ethereum.rlp.RLPException;
import net.consensys.pantheon.ethereum.rlp.RLPInput;
import net.consensys.pantheon.ethereum.rlp.RLPOutput;
import net.consensys.pantheon.util.bytes.BytesValue;
import net.consensys.pantheon.util.uint.UInt256;

public class BlockMetadata {
  private static final BlockMetadata EMPTY = new BlockMetadata(null);
  private final UInt256 totalDifficulty;

  public BlockMetadata(final UInt256 totalDifficulty) {
    this.totalDifficulty = totalDifficulty;
  }

  public static BlockMetadata empty() {
    return EMPTY;
  }

  public static BlockMetadata fromRlp(final BytesValue bytes) {
    return readFrom(RLP.input(bytes));
  }

  public static BlockMetadata readFrom(final RLPInput in) throws RLPException {
    in.enterList();

    final UInt256 totalDifficulty = in.readUInt256Scalar();

    in.leaveList();

    return new BlockMetadata(totalDifficulty);
  }

  public UInt256 getTotalDifficulty() {
    return totalDifficulty;
  }

  public BytesValue toRlp() {
    return RLP.encode(this::writeTo);
  }

  public void writeTo(final RLPOutput out) {
    out.startList();

    out.writeUInt256Scalar(totalDifficulty);

    out.endList();
  }
}
