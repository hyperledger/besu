package net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.blockheaders;

import net.consensys.pantheon.ethereum.chain.BlockAddedEvent;
import net.consensys.pantheon.ethereum.chain.BlockAddedObserver;
import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.BlockResult;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.BlockResultFactory;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionType;

import java.util.List;

public class NewBlockHeadersSubscriptionService implements BlockAddedObserver {

  private final SubscriptionManager subscriptionManager;
  private final BlockchainQueries blockchainQueries;
  private final BlockResultFactory blockResult = new BlockResultFactory();

  public NewBlockHeadersSubscriptionService(
      final SubscriptionManager subscriptionManager, final BlockchainQueries blockchainQueries) {
    this.subscriptionManager = subscriptionManager;
    this.blockchainQueries = blockchainQueries;
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event, final Blockchain blockchain) {
    final List<NewBlockHeadersSubscription> subscribers =
        subscriptionManager.subscriptionsOfType(
            SubscriptionType.NEW_BLOCK_HEADERS, NewBlockHeadersSubscription.class);

    final Hash newBlockHash = event.getBlock().getHash();

    for (final NewBlockHeadersSubscription subscription : subscribers) {
      final BlockResult newBlock =
          subscription.getIncludeTransactions()
              ? blockWithCompleteTransaction(newBlockHash)
              : blockWithTransactionHash(newBlockHash);

      subscriptionManager.sendMessage(subscription.getId(), newBlock);
    }
  }

  private BlockResult blockWithCompleteTransaction(final Hash hash) {
    return blockchainQueries.blockByHash(hash).map(blockResult::transactionComplete).orElse(null);
  }

  private BlockResult blockWithTransactionHash(final Hash hash) {
    return blockchainQueries
        .blockByHashWithTxHashes(hash)
        .map(blockResult::transactionHash)
        .orElse(null);
  }
}
