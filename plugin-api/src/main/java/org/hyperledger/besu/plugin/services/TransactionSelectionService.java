package org.hyperledger.besu.plugin.services;

import org.hyperledger.besu.plugin.services.txselection.TransactionSelectorFactory;

import java.util.Optional;

public interface TransactionSelectionService extends BesuService {

  Optional<TransactionSelectorFactory> getByName(final String name);

  void registerTransactionSeclectorFactory(TransactionSelectorFactory transactionSelectorFactory);
}
