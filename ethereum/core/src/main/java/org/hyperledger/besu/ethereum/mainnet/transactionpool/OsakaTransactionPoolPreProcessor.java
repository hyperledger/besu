/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.mainnet.transactionpool;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.kzg.BlobProofBundle;
import org.hyperledger.besu.ethereum.core.kzg.KzgHelper;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EIP-7594 - Peer Data Availability Sampling - A transaction pre-processor for the Osaka Hard fork,
 * which handles transition logic for blob-related changes.
 *
 * <p>This class supports upgrading locally-submitted transactions that use version 0 KZG blob
 * commitments to the newer version 1 format. Only local transactions that support blob data and
 * contain version 0 commitments are modified. All other transactions are passed through unaltered.
 */
public class OsakaTransactionPoolPreProcessor implements TransactionPoolPreProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(OsakaTransactionPoolPreProcessor.class);

  /**
   * Prepares a transaction for inclusion in the transaction pool.
   *
   * <p>If the transaction is local, supports blobs, and includes version 0 KZG commitments, it is
   * converted to use version 1 blob commitments.
   *
   * @param transaction the transaction to process
   * @param isLocal {@code true} if the transaction originated from a local source
   * @return the processed transaction, which may be upgraded to blobs version 1 if applicable
   */
  @Override
  public Transaction prepareTransaction(final Transaction transaction, final boolean isLocal) {
    if (!shouldProcess(transaction, isLocal)) {
      return transaction;
    }
    return upgradeBlobsWithCommitments(transaction).orElse(transaction);
  }

  private boolean shouldProcess(final Transaction transaction, final boolean isLocal) {
    return isLocal
        && transaction.getType().supportsBlob()
        && transaction
            .getBlobsWithCommitments()
            .map(bwc -> bwc.getVersionId() == BlobProofBundle.VERSION_0_KZG_PROOFS)
            .orElse(false);
  }

  private Optional<Transaction> upgradeBlobsWithCommitments(final Transaction transaction) {
    LOG.debug("Upgrading transaction {} from blobs version 0 to version 1", transaction.getHash());
    return transaction
        .getBlobsWithCommitments()
        .map(KzgHelper::convertToVersion1)
        .map(
            upgradedBlobs ->
                Transaction.builder()
                    .copiedFrom(transaction)
                    .blobsWithCommitments(upgradedBlobs)
                    .build());
  }
}
