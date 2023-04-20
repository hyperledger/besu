package org.hyperledger.besu.plugin.services.txselection;

public interface TransactionSelectorFactory {

  String getName();

  TransactionSelector create();
}
