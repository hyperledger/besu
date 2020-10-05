package org.hyperledger.besu.ethereum.blockcreation;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.EthHashObserver;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.mainnet.EthHashSolver;
import org.hyperledger.besu.ethereum.mainnet.EthHasher;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.util.Subscribers;
import java.util.function.Function;

public class EtcHashMinerExecutor extends EthHashMinerExecutor {

    public EtcHashMinerExecutor(ProtocolContext protocolContext, ProtocolSchedule protocolSchedule, PendingTransactions pendingTransactions, MiningParameters miningParams, AbstractBlockScheduler blockScheduler, Function<Long, Long> gasLimitCalculator) {
        super(protocolContext, protocolSchedule, pendingTransactions, miningParams, blockScheduler, gasLimitCalculator);
    }

    @Override
    public EthHashBlockMiner createMiner(Subscribers<MinedBlockObserver> observers, Subscribers<EthHashObserver> ethHashObservers, BlockHeader parentHeader) {
        final EthHashSolver solver =
                new EthHashSolver(
                        nonceGenerator, new EthHasher.EtcHasher(), stratumMiningEnabled, ethHashObservers);
        final Function<BlockHeader, EthHashBlockCreator> blockCreator =
                (header) ->
                        new EthHashBlockCreator(
                                coinbase.get(),
                                parent -> extraData,
                                pendingTransactions,
                                protocolContext,
                                protocolSchedule,
                                gasLimitCalculator,
                                solver,
                                minTransactionGasPrice,
                                minBlockOccupancyRatio,
                                parentHeader);

        return new EthHashBlockMiner(
                blockCreator, protocolSchedule, protocolContext, observers, blockScheduler, parentHeader);
    }
}
