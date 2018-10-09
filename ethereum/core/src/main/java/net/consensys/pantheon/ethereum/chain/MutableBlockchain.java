package net.consensys.pantheon.ethereum.chain;

import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.TransactionReceipt;

import java.util.List;

public interface MutableBlockchain extends Blockchain {

  /**
   * Adds a block to the blockchain.
   *
   * <p>Block must be connected to the existing blockchain (its parent must already be stored),
   * otherwise an {@link IllegalArgumentException} is thrown. Blocks representing forks are allowed
   * as long as they are connected.
   *
   * @param block The block to append.
   * @param receipts The list of receipts associated with this block's transactions.
   */
  void appendBlock(Block block, List<TransactionReceipt> receipts);
}
