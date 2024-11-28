package org.hyperledger.besu.services;

import org.hyperledger.besu.ethereum.permissioning.account.TransactionPermissioningProvider;
import org.hyperledger.besu.plugin.services.PermissioningService;
import org.hyperledger.besu.plugin.services.permissioning.NodeConnectionPermissioningProvider;
import org.hyperledger.besu.plugin.services.permissioning.NodeMessagePermissioningProvider;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class PermissionServiceImpl implements PermissioningService {
    private static final Logger LOG = LoggerFactory.getLogger(PermissionServiceImpl.class);
    private final List<TransactionPermissioningProvider> transactionPermissioningProviders = new ArrayList<>();

    @Override
    public void registerNodePermissioningProvider(NodeConnectionPermissioningProvider provider) {

    }

    @Override
    public void registerTransactionPermissioningProvider(TransactionPermissioningProvider provider) {
        transactionPermissioningProviders.add(provider);
        LOG.info("Registered new transaction permissioning provider.");
    }

    @Override
    public void registerNodeMessagePermissioningProvider(NodeMessagePermissioningProvider provider) {

    }

    public boolean isTransactionPermitted(Transaction transaction) {
        for (TransactionPermissioningProvider provider : transactionPermissioningProviders) {
            if (!provider.isPermitted(transaction)) {
                LOG.debug("Transaction {} not permitted by one of the providers.", transaction.getHash());
                return false;
            }
        }
        LOG.debug("Transaction {} permitted.", transaction.getHash());
        return true;
    }
}

