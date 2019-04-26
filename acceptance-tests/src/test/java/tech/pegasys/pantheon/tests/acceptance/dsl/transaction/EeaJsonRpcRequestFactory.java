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
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction;

import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.ResponseTypes.PrivateTransactionReceiptResponse;

import java.util.Collections;

import org.assertj.core.util.Lists;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;

public class EeaJsonRpcRequestFactory {

  private final Web3jService web3jService;

  public EeaJsonRpcRequestFactory(final Web3jService web3jService) {
    this.web3jService = web3jService;
  }

  public Request<?, org.web3j.protocol.core.methods.response.EthSendTransaction>
      eeaSendRawTransaction(final String signedTransactionData) {
    return new Request<>(
        "eea_sendRawTransaction",
        Collections.singletonList(signedTransactionData),
        web3jService,
        org.web3j.protocol.core.methods.response.EthSendTransaction.class);
  }

  public Request<?, PrivateTransactionReceiptResponse> eeaGetTransactionReceipt(
      final String txHash) {
    return new Request<>(
        "eea_getTransactionReceipt",
        Lists.newArrayList(txHash),
        web3jService,
        PrivateTransactionReceiptResponse.class);
  }

  public Request<?, EthGetTransactionCount> eeaGetTransactionCount(
      final String accountAddress, final String privacyGroupId) {
    return new Request<>(
        "eea_getTransactionCount",
        Lists.newArrayList(accountAddress, privacyGroupId),
        web3jService,
        EthGetTransactionCount.class);
  }
}
