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
package org.hyperledger.besu.ethereum.encoding;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.Optional;

import com.google.common.collect.ImmutableMap;
import org.apache.tuweni.bytes.Bytes;

public class BerlinRLPFormat extends FrontierRLPFormat {

  private static final ImmutableMap<TransactionType, RLPFormat.Encoder<Transaction>>
      TYPED_TRANSACTION_ENCODERS = ImmutableMap.of();

  @Override
  public void encode(final Transaction transaction, final RLPOutput rlpOutput) {
    if (transaction.getType().equals(TransactionType.FRONTIER)) {
      super.encode(transaction, rlpOutput);
    } else {
      final TransactionType type = transaction.getType();
      final RLPFormat.Encoder<Transaction> encoder =
          Optional.ofNullable(TYPED_TRANSACTION_ENCODERS.get(type))
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          String.format(
                              "Developer Error. A supported transaction type %s has no associated"
                                  + " encoding logic",
                              type)));
      rlpOutput.writeRaw(
          Bytes.concatenate(
              Bytes.of((byte) type.getSerializedType()),
              RLP.encode(output -> encoder.encode(transaction, output))));
    }
  }

  private static final ImmutableMap<TransactionType, RLPFormat.Decoder<Transaction>>
      TYPED_TRANSACTION_DECODERS = ImmutableMap.of();

  public Transaction decodeTransaction(final RLPInput rlpInput) {
    final Bytes typedTransactionBytes = rlpInput.raw();
    final int firstByte = typedTransactionBytes.get(0) & 0xff;
    final TransactionType transactionType = TransactionType.of(firstByte);
    if (transactionType.equals(TransactionType.FRONTIER)) {
      return super.decodeTransaction(rlpInput);
    } else {
      rlpInput.skipNext(); // throw away the type byte
      final RLPFormat.Decoder<Transaction> decoder =
          Optional.ofNullable(TYPED_TRANSACTION_DECODERS.get(transactionType))
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          String.format(
                              "Developer Error. A supported transaction type %s has no associated"
                                  + " decoding logic",
                              transactionType)));
      return decoder.decode(rlpInput);
    }
  }
}
