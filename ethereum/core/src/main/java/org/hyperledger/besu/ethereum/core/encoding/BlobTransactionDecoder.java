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

import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Blob;
import org.hyperledger.besu.datatypes.KZGCommitment;
import org.hyperledger.besu.datatypes.KZGProof;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder.Decoder;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.List;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public class BlobTransactionDecoder implements Decoder {
  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  @Override
  public Transaction decode(final RLPInput input, final EncodingContext context) {
    if (context.equals(EncodingContext.NETWORK)) {
      return decodeNetwork(input);
    }
    return decode(input);
  }

  /*
   * [chain_id, nonce, max_priority_fee_per_gas, max_fee_per_gas, gas_limit, to, value, data,
   * access_list, max_fee_per_blob_gas, blob_versioned_hashes, y_parity, r, s]
   */
  private Transaction decode(final RLPInput input) {
    Transaction transaction;
    input.enterList();
    transaction = readTransactionPayload(input);
    input.leaveList();
    return transaction;
  }

  /*
   * Blob transactions have two network representations.
   * During transaction gossip responses (PooledTransactions),
   * the EIP-2718 TransactionPayload of the blob transaction is wrapped to become:
   *
   * rlp([tx_payload_body, blobs, commitments, proofs])
   */
  private Transaction decodeNetwork(final RLPInput input) {
    input.enterList();
    final Transaction.Builder builder = Transaction.builder();
    input.enterList();
    readTransactionPayloadInner(builder, input);
    input.leaveList();
    List<Blob> blobs = input.readList(Blob::readFrom);
    List<KZGCommitment> commitments = input.readList(KZGCommitment::readFrom);
    List<KZGProof> proofs = input.readList(KZGProof::readFrom);
    input.leaveList();
    builder.kzgBlobs(commitments, blobs, proofs);
    return builder.build();
  }

  private static Transaction readTransactionPayload(final RLPInput input) {
    final Transaction.Builder builder = Transaction.builder();
    readTransactionPayloadInner(builder, input);
    return builder.build();
  }

  private static void readTransactionPayloadInner(
      final Transaction.Builder builder, final RLPInput input) {
    builder
        .type(TransactionType.BLOB)
        .chainId(input.readBigIntegerScalar())
        .nonce(input.readLongScalar())
        .maxPriorityFeePerGas(Wei.of(input.readUInt256Scalar()))
        .maxFeePerGas(Wei.of(input.readUInt256Scalar()))
        .gasLimit(input.readLongScalar())
        .to(input.readBytes(v -> v.size() == 0 ? null : Address.wrap(v)))
        .value(Wei.of(input.readUInt256Scalar()))
        .payload(input.readBytes())
        .accessList(
            input.readList(
                accessListEntryRLPInput -> {
                  accessListEntryRLPInput.enterList();
                  final AccessListEntry accessListEntry =
                      new AccessListEntry(
                          Address.wrap(accessListEntryRLPInput.readBytes()),
                          accessListEntryRLPInput.readList(RLPInput::readBytes32));
                  accessListEntryRLPInput.leaveList();
                  return accessListEntry;
                }))
        .maxFeePerBlobGas(Wei.of(input.readUInt256Scalar()))
        .versionedHashes(
            input.readList(versionedHashes -> new VersionedHash(versionedHashes.readBytes32())));

    final byte recId = (byte) input.readIntScalar();
    builder.signature(
        SIGNATURE_ALGORITHM
            .get()
            .createSignature(
                input.readUInt256Scalar().toUnsignedBigInteger(),
                input.readUInt256Scalar().toUnsignedBigInteger(),
                recId));
  }
}
