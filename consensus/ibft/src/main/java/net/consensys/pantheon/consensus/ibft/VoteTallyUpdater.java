package net.consensys.pantheon.consensus.ibft;

import net.consensys.pantheon.consensus.common.EpochManager;
import net.consensys.pantheon.consensus.common.VoteTally;
import net.consensys.pantheon.consensus.common.VoteType;
import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides the logic to extract vote tally state from the blockchain and update it as blocks are
 * added.
 */
public class VoteTallyUpdater {

  private static final Logger LOG = LogManager.getLogger();
  private static final Address NO_VOTE_SUBJECT = Address.wrap(BytesValue.wrap(new byte[20]));

  private final EpochManager epochManager;

  public VoteTallyUpdater(final EpochManager epochManager) {
    this.epochManager = epochManager;
  }

  /**
   * Create a new VoteTally based on the current blockchain state.
   *
   * @param blockchain the blockchain to load the current state from
   * @return a VoteTally reflecting the state of the blockchain head
   */
  public VoteTally buildVoteTallyFromBlockchain(final Blockchain blockchain) {
    final long chainHeadBlockNumber = blockchain.getChainHeadBlockNumber();
    final long epochBlockNumber = epochManager.getLastEpochBlock(chainHeadBlockNumber);
    LOG.info("Loading validator voting state starting from block {}", epochBlockNumber);
    final BlockHeader epochBlock = blockchain.getBlockHeader(epochBlockNumber).get();
    final List<Address> initialValidators =
        IbftExtraData.decode(epochBlock.getExtraData()).getValidators();
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
      voteTally.discardOutstandingVotes();
      return;
    }

    if (!candidate.equals(NO_VOTE_SUBJECT)) {
      final IbftExtraData ibftExtraData = IbftExtraData.decode(header.getExtraData());
      final Address proposer = IbftBlockHashing.recoverProposerAddress(header, ibftExtraData);
      voteTally.addVote(proposer, candidate, VoteType.fromNonce(header.getNonce()).get());
    }
  }
}
