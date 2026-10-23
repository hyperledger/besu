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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.Configuration.FAIL_ON_UNKNOWN_BUT_NULL;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.core.encoding.EncodingContext;
import org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Deserializes the payload attributes the same way engine_forkchoiceUpdatedV5 does. */
class PayloadAttributesV5Test {

  private static final KeyPair KEY_PAIR = SignatureAlgorithmFactory.getInstance().generateKeyPair();

  private final JsonRpcParameter parameterAccessor = new JsonRpcParameter();

  @Test
  void skipsInclusionListTransactionsThatCannotBeDecoded() throws Exception {
    final Transaction tx1 = new TransactionTestFixture().nonce(0).createTransaction(KEY_PAIR);
    final Transaction tx2 = new TransactionTestFixture().nonce(1).createTransaction(KEY_PAIR);
    final String encodedTx1 = encode(tx1);

    final List<Object> inclusionList = new ArrayList<>();
    inclusionList.add(encode(tx2));
    inclusionList.add("0xnot-hex");
    // truncated encoding of a valid transaction
    inclusionList.add(encodedTx1.substring(0, encodedTx1.length() - 10));
    inclusionList.add(null);
    inclusionList.add(Map.of("not", "a transaction"));
    inclusionList.add(encodedTx1);
    inclusionList.add(encode(tx2));

    final PayloadAttributesV5 attributes = deserialize(inclusionList);

    assertThat(attributes.getInclusionListTransactions()).containsExactly(tx2, tx1);
  }

  @Test
  void deserializesEmptyInclusionList() throws Exception {
    assertThat(deserialize(List.of()).getInclusionListTransactions()).isEmpty();
  }

  @Test
  void inclusionListIsNullWhenAbsent() throws Exception {
    assertThat(deserialize(null).getInclusionListTransactions()).isNull();
  }

  @Test
  void failsWhenInclusionListIsNotAnArray() {
    assertThatExceptionOfType(JsonRpcParameterException.class)
        .isThrownBy(
            () -> deserialize(encode(new TransactionTestFixture().createTransaction(KEY_PAIR))));
  }

  private PayloadAttributesV5 deserialize(final Object inclusionList) throws Exception {
    final Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("timestamp", "0x1");
    attributes.put(
        "prevRandao", "0x0000000000000000000000000000000000000000000000000000000000000000");
    attributes.put("suggestedFeeRecipient", "0x0000000000000000000000000000000000000000");
    attributes.put("withdrawals", List.of());
    attributes.put(
        "parentBeaconBlockRoot",
        "0x0000000000000000000000000000000000000000000000000000000000000000");
    attributes.put("slotNumber", "0x1");
    attributes.put("targetGasLimit", "0x1c9c380");
    if (inclusionList != null) {
      attributes.put("inclusionListTransactions", inclusionList);
    }
    return parameterAccessor
        .optional(new Object[] {attributes}, 0, PayloadAttributesV5.class, FAIL_ON_UNKNOWN_BUT_NULL)
        .orElseThrow();
  }

  private static String encode(final Transaction transaction) {
    return TransactionEncoder.encodeOpaqueBytes(transaction, EncodingContext.BLOCK_BODY)
        .toHexString();
  }
}
