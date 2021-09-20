package org.hyperledger.besu.consensus.merge.headervalidationrules;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.AttachedBlockHeaderValidationRule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MergeUnfinalizedValidationRule implements AttachedBlockHeaderValidationRule {
  private static final Logger LOG = LogManager.getLogger();

  @Override
  public boolean validate(
      final BlockHeader header, final BlockHeader parent, final ProtocolContext protocolContext) {

    MergeContext mergeContext = protocolContext.getConsensusContext(MergeContext.class);
    if (header.getNumber() <= mergeContext.getFinalized()) {
      LOG.warn("BlockHeader failed validation due to block number already finalized");
      return false;
    }

    return true;
  }
}
