/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.sync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.SyncBlockWithReceipts;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;

import java.util.List;
import java.util.function.Function;

/** Persists receipts as soon as they are downloaded. Idempotent: skips if already present. */
public class PersistReceiptsStep implements Function<List<SyncBlockWithReceipts>, List<SyncBlockWithReceipts>> {

  private final MutableBlockchain blockchain;

  public PersistReceiptsStep(final ProtocolContext protocolContext) {
    this.blockchain = protocolContext.getBlockchain();
  }

  @Override
  public List<SyncBlockWithReceipts> apply(final List<SyncBlockWithReceipts> blocksWithReceipts) {
    if (blocksWithReceipts == null || blocksWithReceipts.isEmpty()) {
      return blocksWithReceipts;
    }
    for (final SyncBlockWithReceipts bwr : blocksWithReceipts) {
      final Hash hash = bwr.getHash();
      final List<TransactionReceipt> receipts = bwr.getReceipts();
      if (blockchain.getTxReceipts(hash).isEmpty()) {
        final var updater = ((DefaultBlockchain) blockchain).getBlockchainStorage().updater();
        updater.putTransactionReceipts(hash, receipts);
        updater.commit();
      }
    }
    return blocksWithReceipts;
  }
}
