package tech.pegasys.pantheon.ethereum.core;

@FunctionalInterface
public interface PendingTransactionListener {

  void onTransactionAdded(Transaction transaction);
}
