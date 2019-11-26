/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.crosschain;

import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.lang.reflect.Method;
import java.math.BigInteger;

import org.web3j.protocol.besu.Besu;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.tx.Contract;
import org.web3j.tx.CrosschainTransactionManager;

public class DeployLockableSmartContractTransaction<T extends Contract> implements Transaction<T> {

  private static final BigInteger DEFAULT_GAS_PRICE = BigInteger.valueOf(0);
  private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(3000000);
  private static final Object METHOD_IS_STATIC = null;
  private final CrosschainTransactionManager transactionManager;

  private final Class<T> clazz;

  public DeployLockableSmartContractTransaction(
      final Class<T> clazz, final CrosschainTransactionManager transactionManager) {
    this.clazz = clazz;
    this.transactionManager = transactionManager;
  }

  @Override
  public T execute(final NodeRequests node) {
    try {
      final Method method =
          clazz.getMethod(
              "deployLockable",
              Besu.class,
              CrosschainTransactionManager.class,
              BigInteger.class,
              BigInteger.class);

      final Object invoked =
          method.invoke(
              METHOD_IS_STATIC,
              node.eth(),
              this.transactionManager,
              DEFAULT_GAS_PRICE,
              DEFAULT_GAS_LIMIT);

      return cast(invoked).send();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private RemoteCall<T> cast(final Object invokedMethod) {
    return (RemoteCall<T>) invokedMethod;
  }
}
