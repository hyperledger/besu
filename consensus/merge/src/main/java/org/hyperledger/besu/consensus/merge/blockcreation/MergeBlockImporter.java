package org.hyperledger.besu.consensus.merge.blockcreation;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockImporter;

import java.util.List;

public class MergeBlockImporter extends MainnetBlockImporter {

  public MergeBlockImporter(final BlockValidator blockValidator) {
    super(blockValidator);
  }

  @Override
  public synchronized boolean importBlock(
      final ProtocolContext context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode) {
    if (context.getConsensusContext(MergeContext.class).isPostMerge()) {
      return true;
    }
    return super.importBlock(context, block, headerValidationMode, ommerValidationMode);
  }

  @Override
  public boolean fastImportBlock(
      final ProtocolContext context,
      final Block block,
      final List<TransactionReceipt> receipts,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode) {
    if (context.getConsensusContext(MergeContext.class).isPostMerge()) {
      return true;
    }
    return super.importBlock(context, block, headerValidationMode, ommerValidationMode);
  }


}
