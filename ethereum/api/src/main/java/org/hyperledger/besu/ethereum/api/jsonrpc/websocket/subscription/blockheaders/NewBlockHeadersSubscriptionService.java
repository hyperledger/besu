/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.blockheaders;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

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
  public void onBlockAdded(final BlockAddedEvent event) {
    if (event.isNewCanonicalHead()) {
      final List<BlockHeader> blocks = new ArrayList<>();
      BlockHeader blockPtr = event.getBlock().getHeader();

      while (!blockPtr.getHash().equals(event.getCommonAncestorHash())) {
        blocks.add(blockPtr);

        blockPtr =
            blockchainQueries
                .getBlockchain()
                .getBlockHeader(blockPtr.getParentHash())
                .orElseThrow(() -> new IllegalStateException("The block was on a orphaned chain."));
      }

      Collections.reverse(blocks);
      blocks.forEach(b -> notifySubscribers(b.getHash()));
    }
  }

  private void notifySubscribers(final Hash newBlockHash) {
    subscriptionManager.notifySubscribersOnWorkerThread(
        SubscriptionType.NEW_BLOCK_HEADERS,
        NewBlockHeadersSubscription.class,
        subscribers -> {
          // memoize
          final Supplier<BlockResult> blockWithTx =
              Suppliers.memoize(() -> blockWithCompleteTransaction(newBlockHash));
          final Supplier<BlockResult> blockWithoutTx =
              Suppliers.memoize(() -> blockWithTransactionHash(newBlockHash));

          for (final NewBlockHeadersSubscription subscription : subscribers) {
            final BlockResult newBlock =
                subscription.getIncludeTransactions() ? blockWithTx.get() : blockWithoutTx.get();

            subscriptionManager.sendMessage(subscription.getSubscriptionId(), newBlock);
          }
        });
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
