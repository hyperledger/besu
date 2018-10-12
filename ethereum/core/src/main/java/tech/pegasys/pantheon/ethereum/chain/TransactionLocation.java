package tech.pegasys.pantheon.ethereum.chain;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;

import java.util.Objects;

/**
 * Specifies the location of a transaction within the blockchain (where the transaction was included
 * in the block and location within this block's transactions list).
 */
public class TransactionLocation {

  private final Hash blockHash;
  private final int transactionIndex;

  public TransactionLocation(final Hash blockHash, final int transactionIndex) {
    this.blockHash = blockHash;
    this.transactionIndex = transactionIndex;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public int getTransactionIndex() {
    return transactionIndex;
  }

  public void writeTo(final RLPOutput out) {
    out.startList();

    out.writeBytesValue(blockHash);
    out.writeIntScalar(transactionIndex);

    out.endList();
  }

  public static TransactionLocation readFrom(final RLPInput input) {
    input.enterList();
    final TransactionLocation txLocation =
        new TransactionLocation(Hash.wrap(input.readBytes32()), input.readIntScalar());
    input.leaveList();
    return txLocation;
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof TransactionLocation)) {
      return false;
    }
    final TransactionLocation other = (TransactionLocation) obj;
    return getTransactionIndex() == other.getTransactionIndex()
        && getBlockHash().equals(other.getBlockHash());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getBlockHash(), getTransactionIndex());
  }
}
