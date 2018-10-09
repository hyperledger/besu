package net.consensys.pantheon.ethereum.jsonrpc.internal.queries;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.util.uint.UInt256;

import java.util.List;

public class BlockWithMetadata<T, O> {

  private final BlockHeader header;
  private final List<T> transactions;
  private final List<O> ommers;
  private final UInt256 totalDifficulty;
  private final int size;

  /**
   * @param header The block header
   * @param transactions Block transactions in generic format
   * @param ommers Block ommers in generic format
   * @param totalDifficulty The cumulative difficulty up to and including this block
   * @param size The size of the rlp-encoded block (header + body).
   */
  public BlockWithMetadata(
      final BlockHeader header,
      final List<T> transactions,
      final List<O> ommers,
      final UInt256 totalDifficulty,
      final int size) {
    this.header = header;
    this.transactions = transactions;
    this.ommers = ommers;
    this.totalDifficulty = totalDifficulty;
    this.size = size;
  }

  public BlockHeader getHeader() {
    return header;
  }

  public List<O> getOmmers() {
    return ommers;
  }

  public List<T> getTransactions() {
    return transactions;
  }

  public UInt256 getTotalDifficulty() {
    return totalDifficulty;
  }

  public int getSize() {
    return size;
  }
}
