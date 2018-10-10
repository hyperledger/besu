package net.consensys.pantheon.consensus.clique.blockcreation;

import net.consensys.pantheon.consensus.clique.CliqueContext;
import net.consensys.pantheon.consensus.clique.CliqueExtraData;
import net.consensys.pantheon.consensus.common.EpochManager;
import net.consensys.pantheon.consensus.common.VoteTally;
import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.AbstractBlockScheduler;
import net.consensys.pantheon.ethereum.blockcreation.AbstractMinerExecutor;
import net.consensys.pantheon.ethereum.blockcreation.MiningCoordinator.MinedBlockObserver;
import net.consensys.pantheon.ethereum.blockcreation.MiningParameters;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.PendingTransactions;
import net.consensys.pantheon.ethereum.core.Util;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.util.Subscribers;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.List;
import java.util.concurrent.ExecutorService;

import com.google.common.collect.Lists;

public class CliqueMinerExecutor extends AbstractMinerExecutor<CliqueContext, CliqueBlockMiner> {

  private final Address localAddress;
  private final KeyPair nodeKeys;
  private final EpochManager epochManager;
  private volatile BytesValue vanityData;

  public CliqueMinerExecutor(
      final ProtocolContext<CliqueContext> protocolContext,
      final ExecutorService executorService,
      final ProtocolSchedule<CliqueContext> protocolSchedule,
      final PendingTransactions pendingTransactions,
      final KeyPair nodeKeys,
      final MiningParameters miningParams,
      final AbstractBlockScheduler blockScheduler,
      final EpochManager epochManager) {
    super(
        protocolContext,
        executorService,
        protocolSchedule,
        pendingTransactions,
        miningParams,
        blockScheduler);
    this.nodeKeys = nodeKeys;
    this.localAddress = Util.publicKeyToAddress(nodeKeys.getPublicKey());
    this.epochManager = epochManager;
    this.vanityData = miningParams.getExtraData();
  }

  @Override
  public CliqueBlockMiner startAsyncMining(
      final Subscribers<MinedBlockObserver> observers, final BlockHeader parentHeader) {
    CliqueBlockCreator blockCreator =
        new CliqueBlockCreator(
            localAddress, // TOOD(tmm): This can be removed (used for voting not coinbase).
            this::calculateExtraData,
            pendingTransactions,
            protocolContext,
            protocolSchedule,
            (gasLimit) -> gasLimit,
            nodeKeys,
            minTransactionGasPrice,
            parentHeader);

    CliqueBlockMiner currentRunningMiner =
        new CliqueBlockMiner(
            blockCreator,
            protocolSchedule,
            protocolContext,
            observers,
            blockScheduler,
            parentHeader,
            localAddress);
    executorService.execute(currentRunningMiner);
    return currentRunningMiner;
  }

  public BytesValue calculateExtraData(final BlockHeader parentHeader) {
    List<Address> validators = Lists.newArrayList();

    // Building ON TOP of canonical head, if the next block is epoch, include validators.
    if (epochManager.isEpochBlock(parentHeader.getNumber() + 1)) {
      VoteTally voteTally =
          protocolContext.getConsensusState().getVoteTallyCache().getVoteTallyAtBlock(parentHeader);
      validators.addAll(voteTally.getCurrentValidators());
    }

    CliqueExtraData extraData = new CliqueExtraData(vanityData, null, validators);

    return extraData.encode();
  }
}
