package net.consensys.pantheon.consensus.clique.headervalidationrules;

import net.consensys.pantheon.consensus.clique.CliqueVoteTallyUpdater;
import net.consensys.pantheon.consensus.common.EpochManager;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.mainnet.DetachedBlockHeaderValidationRule;

public class CoinbaseHeaderValidationRule implements DetachedBlockHeaderValidationRule {

  private final EpochManager epochManager;

  public CoinbaseHeaderValidationRule(final EpochManager epochManager) {
    this.epochManager = epochManager;
  }

  @Override
  // The coinbase field is used for voting nodes in/out of the validator group. However, no votes
  // are allowed to be cast on epoch blocks
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    if (epochManager.isEpochBlock(header.getNumber())) {
      return header.getCoinbase().equals(CliqueVoteTallyUpdater.NO_VOTE_SUBJECT);
    }
    return true;
  }
}
