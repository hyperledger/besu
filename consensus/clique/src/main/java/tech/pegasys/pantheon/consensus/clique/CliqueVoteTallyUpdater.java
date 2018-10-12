package tech.pegasys.pantheon.consensus.clique;

import static org.apache.logging.log4j.LogManager.getLogger;

import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.consensus.common.VoteType;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.List;

import org.apache.logging.log4j.Logger;

public class CliqueVoteTallyUpdater {

  private static final Logger LOG = getLogger();
  public static final Address NO_VOTE_SUBJECT = Address.wrap(BytesValue.wrap(new byte[20]));

  private final EpochManager epochManager;

  public CliqueVoteTallyUpdater(final EpochManager epochManager) {
    this.epochManager = epochManager;
  }

  public VoteTally buildVoteTallyFromBlockchain(final Blockchain blockchain) {
    final long chainHeadBlockNumber = blockchain.getChainHeadBlockNumber();
    final long epochBlockNumber = epochManager.getLastEpochBlock(chainHeadBlockNumber);
    LOG.info("Loading validator voting state starting from block {}", epochBlockNumber);
    final BlockHeader epochBlock = blockchain.getBlockHeader(epochBlockNumber).get();
    final List<Address> initialValidators =
        CliqueExtraData.decode(epochBlock.getExtraData()).getValidators();
    final VoteTally voteTally = new VoteTally(initialValidators);
    for (long blockNumber = epochBlockNumber + 1;
        blockNumber <= chainHeadBlockNumber;
        blockNumber++) {
      updateForBlock(blockchain.getBlockHeader(blockNumber).get(), voteTally);
    }
    return voteTally;
  }

  /**
   * Update the vote tally to reflect changes caused by appending a new block to the chain.
   *
   * @param header the header of the block being added
   * @param voteTally the vote tally to update
   */
  public void updateForBlock(final BlockHeader header, final VoteTally voteTally) {
    final Address candidate = header.getCoinbase();
    if (epochManager.isEpochBlock(header.getNumber())) {
      // epoch blocks are not allowed to include a vote
      voteTally.discardOutstandingVotes();
      return;
    }

    if (!candidate.equals(NO_VOTE_SUBJECT)) {
      final CliqueExtraData extraData = CliqueExtraData.decode(header.getExtraData());
      final Address proposer = CliqueBlockHashing.recoverProposerAddress(header, extraData);
      voteTally.addVote(proposer, candidate, VoteType.fromNonce(header.getNonce()).get());
    }
  }
}
