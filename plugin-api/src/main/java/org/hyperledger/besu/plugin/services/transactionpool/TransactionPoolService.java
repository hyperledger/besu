package org.hyperledger.besu.plugin.services.transactionpool;

import org.hyperledger.besu.plugin.services.BesuService;

public interface TransactionPoolService extends BesuService {
    void disableTransactionPool();

    void enableTransactionPool();
}
