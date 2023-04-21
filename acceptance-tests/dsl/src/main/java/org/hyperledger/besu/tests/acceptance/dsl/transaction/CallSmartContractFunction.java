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
package org.hyperledger.besu.tests.acceptance.dsl.transaction;

import org.hyperledger.besu.tests.acceptance.dsl.account.Accounts;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;

public class CallSmartContractFunction implements Transaction<EthSendTransaction> {

  private static final BigInteger GAS_PRICE = BigInteger.valueOf(1000);

  private static final Credentials BENEFACTOR_ONE =
      Credentials.create(Accounts.GENESIS_ACCOUNT_ONE_PRIVATE_KEY);

  private BigInteger gasLimit = BigInteger.valueOf(3000000);
  private final String functionCall;
  private final String contractAddress;

  public CallSmartContractFunction(final String contractAddress, final String functionName) {
    final Function function =
        new Function(functionName, Collections.emptyList(), Collections.emptyList());

    this.contractAddress = contractAddress;
    this.functionCall = FunctionEncoder.encode(function);
  }

  public CallSmartContractFunction(
      final String contractAddress, final String functionCall, final BigInteger gasLimit) {
    this.contractAddress = contractAddress;
    this.functionCall = functionCall;
    this.gasLimit = gasLimit;
  }

  @Override
  public EthSendTransaction execute(final NodeRequests node) {
    final RawTransactionManager transactionManager =
        new RawTransactionManager(node.eth(), BENEFACTOR_ONE);
    try {
      return transactionManager.sendTransaction(
          GAS_PRICE, gasLimit, contractAddress, functionCall, BigInteger.ZERO);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
