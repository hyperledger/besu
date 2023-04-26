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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.contract;

import org.hyperledger.besu.tests.acceptance.dsl.transaction.CallSmartContractFunction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.DeploySmartContractTransaction;

import java.math.BigInteger;

import org.web3j.tx.Contract;

public class ContractTransactions {

  public <T extends Contract> DeploySmartContractTransaction<T> createSmartContract(
      final Class<T> clazz) {
    return new DeploySmartContractTransaction<>(clazz);
  }

  public <T extends Contract> DeploySmartContractTransaction<T> createSmartContract(
      final Class<T> clazz, final Object... args) {
    return new DeploySmartContractTransaction<>(clazz, args);
  }

  public CallSmartContractFunction callSmartContract(
      final String contractAddress, final String functionName) {
    return new CallSmartContractFunction(contractAddress, functionName);
  }

  public CallSmartContractFunction callSmartContract(
      final String contractAddress, final String functionCall, final BigInteger gasLimit) {
    return new CallSmartContractFunction(contractAddress, functionCall, gasLimit);
  }
}
