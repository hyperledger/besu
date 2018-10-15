package tech.pegasys.pantheon.consensus.ibft;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;

import java.util.List;

/**
 * The IBFT BlockImporter implementation. Adds votes to VoteTally as blocks are added to the chain.
 */
public class IbftBlockImporter implements BlockImporter<IbftContext> {

  private final BlockImporter<IbftContext> delegate;
  private final VoteTallyUpdater voteTallyUpdater;

  public IbftBlockImporter(
      final BlockImporter<IbftContext> delegate, final VoteTallyUpdater voteTallyUpdater) {
    this.delegate = delegate;
    this.voteTallyUpdater = voteTallyUpdater;
  }

  @Override
  public boolean importBlock(
      final ProtocolContext<IbftContext> context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode) {
    final boolean result =
        delegate.importBlock(context, block, headerValidationMode, ommerValidationMode);
    updateVoteTally(result, block.getHeader(), context);
    return result;
  }

  @Override
  public boolean fastImportBlock(
      final ProtocolContext<IbftContext> context,
      final Block block,
      final List<TransactionReceipt> receipts,
      final HeaderValidationMode headerValidationMode) {
    final boolean result = delegate.fastImportBlock(context, block, receipts, headerValidationMode);
    updateVoteTally(result, block.getHeader(), context);
    return result;
  }

  private void updateVoteTally(
      final boolean result, final BlockHeader header, final ProtocolContext<IbftContext> context) {
    if (result) {
      voteTallyUpdater.updateForBlock(header, context.getConsensusState().getVoteTally());
    }
  }
}
