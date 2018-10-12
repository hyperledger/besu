package tech.pegasys.pantheon.ethereum.core;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;

import java.util.List;

/**
 * An interface for a block importer.
 *
 * <p>The block importer is responsible for assessing whether a candidate block can be added to a
 * given blockchain given the block history and its corresponding state. If the block is able to be
 * successfully added, the corresponding blockchain and world state will be updated as well.
 */
public interface BlockImporter<C> {

  /**
   * Attempts to import the given block to the specificed blockchain and world state.
   *
   * @param context The context to attempt to update
   * @param block The block
   * @param headerValidationMode Determines the validation to perform on this header.
   * @return {@code true} if the block was added somewhere in the blockchain; otherwise {@code
   *     false}
   */
  boolean importBlock(
      ProtocolContext<C> context, Block block, HeaderValidationMode headerValidationMode);

  /**
   * Attempts to import the given block. Uses "fast" validation. Performs light validation using the
   * block's receipts rather than processing all transactions and fully validating world state.
   *
   * @param context The context to attempt to update
   * @param block The block
   * @param receipts The receipts associated with this block.
   * @param headerValidationMode Determines the validation to perform on this header.
   * @return {@code true} if the block was added somewhere in the blockchain; otherwise {@code
   *     false}
   */
  boolean fastImportBlock(
      ProtocolContext<C> context,
      Block block,
      List<TransactionReceipt> receipts,
      HeaderValidationMode headerValidationMode);
}
