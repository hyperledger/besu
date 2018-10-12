package tech.pegasys.pantheon.consensus.clique.headervalidationrules;

import tech.pegasys.pantheon.consensus.clique.CliqueVoteTallyUpdater;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.mainnet.DetachedBlockHeaderValidationRule;

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
