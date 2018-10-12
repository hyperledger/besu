package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;

import java.util.List;

/** Processes a block. */
public interface BlockProcessor {

  /** A block processing result. */
  interface Result {

    /**
     * The receipts generated for the transactions in a block
     *
     * <p>This is only valid when {@code BlockProcessor#isSuccessful} returns {@code true}.
     *
     * @return the receipts generated for the transactions the a block
     */
    List<TransactionReceipt> getReceipts();

    /**
     * Returns whether the block was successfully processed.
     *
     * @return {@code true} if the block was processed successfully; otherwise {@code false}
     */
    boolean isSuccessful();
  }

  /**
   * Processes the block.
   *
   * @param blockchain the blockchain to append the block to
   * @param worldState the world state to apply changes to
   * @param block the block to process
   * @return the block processing result
   */
  default Result processBlock(
      final Blockchain blockchain, final MutableWorldState worldState, final Block block) {
    return processBlock(
        blockchain,
        worldState,
        block.getHeader(),
        block.getBody().getTransactions(),
        block.getBody().getOmmers());
  }

  /**
   * Processes the block.
   *
   * @param blockchain the blockchain to append the block to
   * @param worldState the world state to apply changes to
   * @param blockHeader the block header for the block
   * @param transactions the transactions in the block
   * @param ommers the block ommers
   * @return the block processing result
   */
  Result processBlock(
      Blockchain blockchain,
      MutableWorldState worldState,
      BlockHeader blockHeader,
      List<Transaction> transactions,
      List<BlockHeader> ommers);
}
