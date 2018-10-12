package tech.pegasys.pantheon.consensus.clique.blockcreation;

import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.consensus.clique.CliqueExtraData;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractBlockScheduler;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractMinerExecutor;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator.MinedBlockObserver;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.PendingTransactions;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.util.Subscribers;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

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

    final BytesValue vanityDataToInsert = createCorrectlySizedVanityData();
    // Building ON TOP of canonical head, if the next block is epoch, include validators.
    if (epochManager.isEpochBlock(parentHeader.getNumber() + 1)) {
      final VoteTally voteTally =
          protocolContext.getConsensusState().getVoteTallyCache().getVoteTallyAtBlock(parentHeader);
      validators.addAll(voteTally.getCurrentValidators());
    }

    final CliqueExtraData extraData = new CliqueExtraData(vanityDataToInsert, null, validators);

    return extraData.encode();
  }

  private BytesValue createCorrectlySizedVanityData() {
    int vanityPadding = Math.max(0, CliqueExtraData.EXTRA_VANITY_LENGTH - vanityData.size());
    return BytesValues.concatenate(BytesValue.wrap(new byte[vanityPadding]), vanityData)
        .slice(0, CliqueExtraData.EXTRA_VANITY_LENGTH);
  }
}
