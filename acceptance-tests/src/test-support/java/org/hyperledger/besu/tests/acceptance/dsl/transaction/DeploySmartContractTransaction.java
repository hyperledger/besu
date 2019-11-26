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
package org.hyperledger.besu.tests.acceptance.dsl.transaction;

import org.hyperledger.besu.tests.acceptance.dsl.account.Accounts;

import java.lang.reflect.Method;
import java.math.BigInteger;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.tx.Contract;

public class DeploySmartContractTransaction<T extends Contract> implements Transaction<T> {

  private static final BigInteger DEFAULT_GAS_PRICE = BigInteger.valueOf(1000);
  private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(3000000);
  private static final Object METHOD_IS_STATIC = null;
  private static final Credentials BENEFACTOR_ONE =
      Credentials.create(Accounts.GENESIS_ACCOUNT_ONE_PRIVATE_KEY);

  int option;

  private final Class<T> clazz;

  private String string;
  private BigInteger bigInteger;

  public DeploySmartContractTransaction(final Class<T> clazz) {
    this.clazz = clazz;
    this.option = 0;
  }

  public DeploySmartContractTransaction(
      final Class<T> clazz, final String string, final BigInteger bigInteger) {
    this.clazz = clazz;
    this.string = string;
    this.bigInteger = bigInteger;
    this.option = 1;
  }

  @Override
  public T execute(final NodeRequests node) {
    try {
      final Method method;
      final Object invoked;
      switch (this.option) {
        case 0:
          method =
              clazz.getMethod(
                  "deploy", Web3j.class, Credentials.class, BigInteger.class, BigInteger.class);
          invoked =
              method.invoke(
                  METHOD_IS_STATIC,
                  node.eth(),
                  BENEFACTOR_ONE,
                  DEFAULT_GAS_PRICE,
                  DEFAULT_GAS_LIMIT);
          break;
        case 1:
          method =
              clazz.getMethod(
                  "deploy",
                  Web3j.class,
                  Credentials.class,
                  BigInteger.class,
                  BigInteger.class,
                  String.class,
                  BigInteger.class);
          invoked =
              method.invoke(
                  METHOD_IS_STATIC,
                  node.eth(),
                  BENEFACTOR_ONE,
                  DEFAULT_GAS_PRICE,
                  DEFAULT_GAS_LIMIT,
                  this.string,
                  this.bigInteger);
          break;
        default:
          throw new Error();
      }

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
