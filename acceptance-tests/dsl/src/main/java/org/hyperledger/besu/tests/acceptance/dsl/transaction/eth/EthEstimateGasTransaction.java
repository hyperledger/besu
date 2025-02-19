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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.eth;

import static org.web3j.protocol.core.DefaultBlockParameterName.LATEST;

import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;
import java.math.BigInteger;

import org.web3j.protocol.core.methods.response.EthEstimateGas;

public class EthEstimateGasTransaction implements Transaction<EthEstimateGas> {
  private final String contractAddress;
  private final String functionCall;
  private final String from = "";

  public EthEstimateGasTransaction(final String contractAddress, final String functionCall) {
    this.contractAddress = contractAddress;
    this.functionCall = functionCall;
  }

  @Override
  public EthEstimateGas execute(final NodeRequests node) {
    try {

      var nonce = node.eth().ethGetTransactionCount(from, LATEST).send().getTransactionCount();

      return node.eth()
          .ethEstimateGas(
              new org.web3j.protocol.core.methods.request.Transaction(
                  from,
                  nonce,
                  BigInteger.ZERO,
                  BigInteger.ZERO,
                  contractAddress,
                  BigInteger.ZERO,
                  functionCall))
          .send();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
