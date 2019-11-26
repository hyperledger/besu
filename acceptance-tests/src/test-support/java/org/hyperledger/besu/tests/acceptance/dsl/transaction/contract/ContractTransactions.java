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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.contract;

import org.hyperledger.besu.tests.acceptance.dsl.transaction.CallSmartContractFunction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.DeploySmartContractTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.crosschain.DeployLockableSmartContractTransaction;

import java.math.BigInteger;

import org.web3j.tx.Contract;
import org.web3j.tx.CrosschainTransactionManager;

public class ContractTransactions {

  public <T extends Contract> DeploySmartContractTransaction<T> createSmartContract(
      final Class<T> clazz) {
    return new DeploySmartContractTransaction<>(clazz);
  }

  public <T extends Contract> DeploySmartContractTransaction<T> createSmartContract(
      final Class<T> clazz, final String string, final BigInteger bigInteger) {
    return new DeploySmartContractTransaction<>(clazz, string, bigInteger);
  }

  public <T extends Contract> DeployLockableSmartContractTransaction<T> createLockableSmartContract(
      final Class<T> clazz, final CrosschainTransactionManager transactionManager) {
    return new DeployLockableSmartContractTransaction<>(clazz, transactionManager);
  }

  public CallSmartContractFunction callSmartContract(
      final String functionName, final String contractAddress) {
    return new CallSmartContractFunction(functionName, contractAddress);
  }
}
