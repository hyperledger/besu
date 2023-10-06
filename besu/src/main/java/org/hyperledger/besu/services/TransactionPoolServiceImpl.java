package org.hyperledger.besu.services;

import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.plugin.services.transactionpool.TransactionPoolService;

public class TransactionPoolServiceImpl implements TransactionPoolService {

    private final TransactionPool transactionPool;

    public TransactionPoolServiceImpl(final TransactionPool transactionPool) {
        this.transactionPool = transactionPool;
    }

    @Override
    public void disableTransactionPool(){
        transactionPool.setDisabled();
    }

    @Override
    public void enableTransactionPool(){
        transactionPool.setEnabled();
    }
}
