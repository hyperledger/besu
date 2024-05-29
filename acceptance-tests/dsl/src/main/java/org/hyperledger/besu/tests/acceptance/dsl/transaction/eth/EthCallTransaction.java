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
import static org.web3j.tx.gas.DefaultGasProvider.GAS_LIMIT;

import org.hyperledger.besu.tests.acceptance.dsl.account.Accounts;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;
import java.math.BigInteger;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.EthCall;

public class EthCallTransaction implements Transaction<EthCall> {
  private final String contractAddress;
  private final String functionCall;
  private BigInteger gasLimit = GAS_LIMIT;
  private final String benefactorOneAddress =
      Credentials.create(Accounts.GENESIS_ACCOUNT_ONE_PRIVATE_KEY).getAddress();

  public EthCallTransaction(final String contractAddress, final String functionCall) {
    this.contractAddress = contractAddress;
    this.functionCall = functionCall;
  }

  public EthCallTransaction(
      final String contractAddress, final String functionCall, final BigInteger gasLimit) {
    this.contractAddress = contractAddress;
    this.functionCall = functionCall;
    this.gasLimit = gasLimit;
  }

  @Override
  public EthCall execute(final NodeRequests node) {
    try {

      var transactionCount =
          node.eth()
              .ethGetTransactionCount(benefactorOneAddress, LATEST)
              .send()
              .getTransactionCount();

      var transaction =
          new org.web3j.protocol.core.methods.request.Transaction(
              benefactorOneAddress,
              transactionCount,
              BigInteger.ZERO,
              gasLimit,
              contractAddress,
              BigInteger.ZERO,
              functionCall);

      return node.eth().ethCall(transaction, LATEST).send();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
