/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.tests.acceptance.dsl.privacy;

import static org.junit.Assert.assertEquals;

import tech.pegasys.pantheon.tests.acceptance.dsl.jsonrpc.Eea;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.ResponseTypes;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transactions;

import java.math.BigInteger;

import org.web3j.utils.Numeric;

public class ExpectValidPrivateContractEventsEmitted extends GetValidPrivateTransactionReceipt {
  private final String eventValue;

  public ExpectValidPrivateContractEventsEmitted(
      final String eventValue, final Eea eea, final Transactions transactions) {
    super(eea, transactions);
    this.eventValue = eventValue;
  }

  public void verify(final PantheonNode node, final String transactionHash) {
    ResponseTypes.PrivateTransactionReceipt privateTxReceipt =
        getPrivateTransactionReceipt(node, transactionHash);

    String event = privateTxReceipt.getLogs().get(0).getData().substring(66, 130);
    assertEquals(
        new BigInteger(eventValue), Numeric.decodeQuantity(Numeric.prependHexPrefix(event)));
  }
}
