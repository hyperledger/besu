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
package org.hyperledger.besu.ethereum.core.encoding;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.SetCodeAuthorization;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public class SetCodeTransactionDecoder {
  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  private SetCodeTransactionDecoder() {
    // private constructor
  }

  public static Transaction decode(final RLPInput input) {
    input.enterList();
    final BigInteger chainId = input.readBigIntegerScalar();
    final Transaction.Builder builder =
        Transaction.builder()
            .type(TransactionType.SET_CODE)
            .chainId(chainId)
            .nonce(input.readLongScalar())
            .maxPriorityFeePerGas(Wei.of(input.readUInt256Scalar()))
            .maxFeePerGas(Wei.of(input.readUInt256Scalar()))
            .gasLimit(input.readLongScalar())
            .to(input.readBytes(v -> v.isEmpty() ? null : Address.wrap(v)))
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
            .setCodeTransactionPayloads(
                input.readList(
                    setCodeTransactionPayloadsRLPInput ->
                        decodeInnerPayload(setCodeTransactionPayloadsRLPInput)));

    final byte recId = (byte) input.readUnsignedByteScalar();
    final BigInteger r = input.readUInt256Scalar().toUnsignedBigInteger();
    final BigInteger s = input.readUInt256Scalar().toUnsignedBigInteger();

    input.leaveList();

    final Transaction transaction =
        builder.signature(SIGNATURE_ALGORITHM.get().createSignature(r, s, recId)).build();

    return transaction;
  }

  public static org.hyperledger.besu.datatypes.SetCodeAuthorization decodeInnerPayload(
      final RLPInput input) {
    input.enterList();
    final BigInteger chainId = input.readBigIntegerScalar();
    final Address address = Address.wrap(input.readBytes());

    Optional<Long> nonce = Optional.empty();

    if (!input.nextIsList()) {
      throw new IllegalArgumentException("Optional nonce must be an list, but isn't");
    }

    final long noncesSize = input.nextSize();

    input.enterList();
    if (noncesSize == 1) {
      nonce = Optional.ofNullable(input.readLongScalar());
    } else if (noncesSize > 1) {
      throw new IllegalArgumentException("Nonce list may only have 1 member, if any");
    }
    input.leaveList();

    final byte yParity = (byte) input.readUnsignedByteScalar();
    final BigInteger r = input.readUInt256Scalar().toUnsignedBigInteger();
    final BigInteger s = input.readUInt256Scalar().toUnsignedBigInteger();

    input.leaveList();

    final SECPSignature signature = SIGNATURE_ALGORITHM.get().createSignature(r, s, yParity);

    return new SetCodeAuthorization(chainId, address, nonce, signature);
  }
}
