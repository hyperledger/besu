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
package org.hyperledger.besu.ethereum.core.encoding;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.plugin.data.TransactionType;

import com.google.common.collect.ImmutableMap;

public class TransactionRLPEncoderDecoder {

  private static final TransactionRLPEncoder DEFAULT_ENCODER = TransactionRLPEncoder.FRONTIER;
  private static final ImmutableMap<TransactionType, TransactionRLPEncoder> ENCODERS =
      ImmutableMap.of(
          TransactionType.FRONTIER,
          TransactionRLPEncoder.FRONTIER,
          TransactionType.EIP1559,
          TransactionRLPEncoder.EIP1559);

  public static void encode(final Transaction transaction, final RLPOutput output) {
    ENCODERS.getOrDefault(transaction.getType(), DEFAULT_ENCODER).encode(transaction, output);
  }

  public static Transaction decode(final RLPInput input) {
    final TransactionRLPDecoder decoder =
        ExperimentalEIPs.eip1559Enabled
            ? TransactionRLPDecoder.EIP1559
            : TransactionRLPDecoder.FRONTIER;
    return decoder.decode(input);
  }
}
