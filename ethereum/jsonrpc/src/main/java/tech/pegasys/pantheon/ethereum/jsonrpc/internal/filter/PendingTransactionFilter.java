package tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter;

import tech.pegasys.pantheon.ethereum.core.Hash;

import java.util.ArrayList;
import java.util.List;

/** Tracks new pending transactions that have arrived in the transaction pool */
class PendingTransactionFilter extends Filter {

  private final List<Hash> transactionHashes = new ArrayList<>();

  PendingTransactionFilter(final String id) {
    super(id);
  }

  void addTransactionHash(final Hash hash) {
    transactionHashes.add(hash);
  }

  List<Hash> transactionHashes() {
    return transactionHashes;
  }

  void clearTransactionHashes() {
    transactionHashes.clear();
  }
}
