package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;

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
   * @param ommerValidationMode The validation mode to use for ommer headers
   * @return {@code true} if valid; otherwise {@code false}
   */
  boolean validateBody(
      ProtocolContext<C> context,
      Block block,
      List<TransactionReceipt> receipts,
      Hash worldStateRootHash,
      final HeaderValidationMode ommerValidationMode);

  /**
   * Validates that the block body is valid, but skips state root validation.
   *
   * @param context The context to validate against
   * @param block The block to validate
   * @param receipts The receipts that correspond to the blocks transactions
   * @param ommerValidationMode The validation mode to use for ommer headers
   * @return {@code true} if valid; otherwise {@code false}
   */
  boolean validateBodyLight(
      ProtocolContext<C> context,
      Block block,
      List<TransactionReceipt> receipts,
      final HeaderValidationMode ommerValidationMode);
}
