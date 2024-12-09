package org.hyperledger.besu.plugin.services.permissioning;

import org.hyperledger.besu.datatypes.Transaction;

@FunctionalInterface
public interface TransactionPermissioningProvider {
    boolean isPermitted(final Transaction transaction);
}
