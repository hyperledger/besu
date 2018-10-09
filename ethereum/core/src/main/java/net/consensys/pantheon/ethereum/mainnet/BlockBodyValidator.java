package net.consensys.pantheon.ethereum.mainnet;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.TransactionReceipt;

import java.util.List;

/** Validates block bodies. */
public interface BlockBodyValidator<C> {

  /**
   * Validates that the block body is valid.
   *
   * @param context The context to validate against
   * @param block The block to validate
   * @param receipts The receipts that correspond to the blocks transactions
   * @param worldStateRootHash The rootHash defining the world state after processing this block and
   *     all of its transactions.
   * @return {@code true} if valid; otherwise {@code false}
   */
  boolean validateBody(
      ProtocolContext<C> context,
      Block block,
      List<TransactionReceipt> receipts,
      Hash worldStateRootHash);

  /**
   * Validates that the block body is valid, but skips state root validation.
   *
   * @param context The context to validate against
   * @param block The block to validate
   * @param receipts The receipts that correspond to the blocks transactions
   * @return {@code true} if valid; otherwise {@code false}
   */
  boolean validateBodyLight(
      ProtocolContext<C> context, Block block, List<TransactionReceipt> receipts);
}
