/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.core.encoding;

import static org.slf4j.LoggerFactory.getLogger;

import org.hyperledger.besu.datatypes.Blob;
import org.hyperledger.besu.datatypes.KZGCommitment;
import org.hyperledger.besu.datatypes.KZGProof;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import org.slf4j.Logger;

public class BlobPooledTransactionEncoder {
  private static final Logger LOG = getLogger(BlobPooledTransactionEncoder.class);

  public static void encode(final Transaction transaction, final RLPOutput out) {
    LOG.trace("Encoding transaction with blobs {}", transaction);
    out.startList();
    var blobsWithCommitments = transaction.getBlobsWithCommitments().orElseThrow();
    BlobTransactionEncoder.encode(transaction, out);
    out.writeList(blobsWithCommitments.getBlobs(), Blob::writeTo);
    out.writeList(blobsWithCommitments.getKzgCommitments(), KZGCommitment::writeTo);
    out.writeList(blobsWithCommitments.getKzgProofs(), KZGProof::writeTo);
    out.endList();
  }
}
