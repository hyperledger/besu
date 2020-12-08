/*
 *
 *  * Copyright ConsenSys AG.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.hyperledger.besu.ethereum.encoding;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.Optional;

import com.google.common.collect.ImmutableMap;

public class BerlinRLPSpec extends FrontierRLPSpec {

  private static final ImmutableMap<TransactionType, ProtocolRLPSpec.Decoder<Transaction>>
      TYPED_TRANSACTION_DECODERS = ImmutableMap.of();

  @Override
  public Transaction decodeTransaction(final RLPInput rlpInput) {
    if (rlpInput.nextIsList()) {
      return super.decodeTransaction(rlpInput);
    } else {
      final int firstByte = rlpInput.readByte();
      final TransactionType transactionType = TransactionType.of(firstByte);
      final ProtocolRLPSpec.Decoder<Transaction> decoder =
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
