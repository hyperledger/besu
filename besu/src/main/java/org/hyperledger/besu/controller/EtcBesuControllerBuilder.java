package org.hyperledger.besu.controller;

import org.hyperledger.besu.config.CliqueConfigOptions;
import org.hyperledger.besu.config.EtchashConfigOptions;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.*;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

public class EtcBesuControllerBuilder extends MainnetBesuControllerBuilder {
    private long activationBlock;

    @Override
    protected void prepForBuild() {
        final EtchashConfigOptions etchashConfig =
                genesisConfig.getConfigOptions(genesisConfigOverrides).getEtchashConfigOptions();
        activationBlock = etchashConfig.getEpochLengthActivationBlock().getAsLong();
    }

    @Override
    protected MiningCoordinator createMiningCoordinator(ProtocolSchedule protocolSchedule, ProtocolContext protocolContext, TransactionPool transactionPool, MiningParameters miningParameters, SyncState syncState, EthProtocolManager ethProtocolManager) {
        final EtcHashMinerExecutor executor =
                new EtcHashMinerExecutor(
                        protocolContext,
                        protocolSchedule,
                        transactionPool.getPendingTransactions(),
                        miningParameters,
                        new DefaultBlockScheduler(
                                MainnetBlockHeaderValidator.MINIMUM_SECONDS_SINCE_PARENT,
                                MainnetBlockHeaderValidator.TIMESTAMP_TOLERANCE_S,
                                clock),
                        gasLimitCalculator,
                        activationBlock);

        final EthHashMiningCoordinator miningCoordinator =
                new EthHashMiningCoordinator(
                        protocolContext.getBlockchain(),
                        executor,
                        syncState,
                        miningParameters.getRemoteSealersLimit(),
                        miningParameters.getRemoteSealersTimeToLive());
        miningCoordinator.addMinedBlockObserver(ethProtocolManager);
        miningCoordinator.setStratumMiningEnabled(miningParameters.isStratumMiningEnabled());
        if (miningParameters.isMiningEnabled()) {
            miningCoordinator.enable();
        }

        return miningCoordinator;
    }
}
