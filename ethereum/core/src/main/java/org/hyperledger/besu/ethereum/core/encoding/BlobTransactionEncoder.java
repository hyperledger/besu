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
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;

public class BlobTransactionEncoder {
  private static final Logger LOG = getLogger(BlobTransactionEncoder.class);

  public static void encodeEIP4844(final Transaction transaction, final RLPOutput out) {
    out.startList();
    out.writeBigIntegerScalar(transaction.getChainId().orElseThrow());
    out.writeLongScalar(transaction.getNonce());
    out.writeUInt256Scalar(transaction.getMaxPriorityFeePerGas().orElseThrow());
    out.writeUInt256Scalar(transaction.getMaxFeePerGas().orElseThrow());
    out.writeLongScalar(transaction.getGasLimit());
    out.writeBytes(transaction.getTo().map(Bytes::copy).orElse(Bytes.EMPTY));
    out.writeUInt256Scalar(transaction.getValue());
    out.writeBytes(transaction.getPayload());
    TransactionEncoder.writeAccessList(out, transaction.getAccessList());
    out.writeUInt256Scalar(transaction.getMaxFeePerDataGas().orElseThrow());
    out.startList();
    transaction
        .getVersionedHashes()
        .get()
        .forEach(
            vh -> {
              out.writeBytes(vh.toBytes());
            });
    out.endList();
    TransactionEncoder.writeSignatureAndRecoveryId(transaction, out);
    out.endList();
  }

  private static void encodeEIP4844Network(final Transaction transaction, final RLPOutput out) {
    LOG.trace("Encoding transaction with blobs {}", transaction);
    out.startList();
    var blobsWithCommitments = transaction.getBlobsWithCommitments().orElseThrow();
    encodeEIP4844(transaction, out);

    out.writeList(blobsWithCommitments.getBlobs(), Blob::writeTo);
    out.writeList(blobsWithCommitments.getKzgCommitments(), KZGCommitment::writeTo);
    out.writeList(blobsWithCommitments.getKzgProofs(), KZGProof::writeTo);
    out.endList();
  }

  public static void encodeForWireNetwork(
      final Transaction transaction, final RLPOutput rlpOutput) {
    rlpOutput.writeBytes(encodeOpaqueBytesNetwork(transaction));
  }

  private static Bytes encodeOpaqueBytesNetwork(final Transaction transaction) {
    return Bytes.concatenate(
        Bytes.of(transaction.getType().getSerializedType()),
        RLP.encode(rlpOutput -> encodeEIP4844Network(transaction, rlpOutput)));
  }

  public static void writeBlobVersionedHashes(
      final RLPOutput rlpOutput, final List<VersionedHash> versionedHashes) {
    rlpOutput.writeList(versionedHashes, (h, out) -> out.writeBytes(h.toBytes()));
  }
}
