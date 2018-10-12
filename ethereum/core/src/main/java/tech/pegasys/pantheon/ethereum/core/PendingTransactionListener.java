package net.consensys.pantheon.ethereum.core;

@FunctionalInterface
public interface PendingTransactionListener {

  void onTransactionAdded(Transaction transaction);
}
