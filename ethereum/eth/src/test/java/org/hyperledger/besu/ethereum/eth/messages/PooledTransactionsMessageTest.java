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
package org.hyperledger.besu.ethereum.eth.messages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.Arrays;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class PooledTransactionsMessageTest {

  @Test
  public void roundTripPooledTransactionsMessage() {
    List<Transaction> tx =
        Arrays.asList(
            Transaction.builder()
                .type(TransactionType.FRONTIER)
                .nonce(42)
                .gasLimit(654321)
                .gasPrice(Wei.of(2))
                .value(Wei.of(1337))
                .payload(Bytes.EMPTY)
                .signAndBuild(SignatureAlgorithmFactory.getInstance().generateKeyPair()));
    final PooledTransactionsMessage msg = PooledTransactionsMessage.create(tx);
    assertThat(msg.getCode()).isEqualTo(EthPV65.POOLED_TRANSACTIONS);
    assertThat(msg.transactions()).isEqualTo(tx);
  }

  @Test
  public void readFromMessageWithWrongCodeThrows() {
    final RawMessage rawMsg = new RawMessage(EthPV62.BLOCK_HEADERS, Bytes.of(0));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> PooledTransactionsMessage.readFrom(rawMsg));
  }
}
