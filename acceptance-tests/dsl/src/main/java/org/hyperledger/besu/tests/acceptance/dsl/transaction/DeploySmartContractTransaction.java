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

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

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

  private final Class<T> clazz;
  private final Object[] args;

  public DeploySmartContractTransaction(final Class<T> clazz, final Object... args) {
    this.clazz = clazz;
    this.args = args;
  }

  @Override
  public T execute(final NodeRequests node) {
    try {
      if (args != null && args.length != 0) {
        final ArrayList<Object> parameterObjects = new ArrayList<>();
        parameterObjects.addAll(
            Arrays.asList(node.eth(), BENEFACTOR_ONE, DEFAULT_GAS_PRICE, DEFAULT_GAS_LIMIT));
        parameterObjects.addAll(Arrays.asList(args));

        final Method method =
            Arrays.stream(clazz.getMethods())
                .filter(
                    i ->
                        i.getName().equals("deploy")
                            && parameterTypesAreEqual(i.getParameterTypes(), parameterObjects))
                .findAny()
                .orElseThrow();

        final Object invoked = method.invoke(METHOD_IS_STATIC, parameterObjects.toArray());

        return cast(invoked).send();
      } else {
        final Method method =
            clazz.getMethod(
                "deploy", Web3j.class, Credentials.class, BigInteger.class, BigInteger.class);

        final Object invoked =
            method.invoke(
                METHOD_IS_STATIC, node.eth(), BENEFACTOR_ONE, DEFAULT_GAS_PRICE, DEFAULT_GAS_LIMIT);

        return cast(invoked).send();
      }

    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("rawtypes")
  private boolean parameterTypesAreEqual(
      final Class<?>[] expectedTypes, final ArrayList<Object> actualObjects) {
    if (expectedTypes.length != actualObjects.size()) {
      return false;
    }
    final ArrayList<Class> actualTypes =
        actualObjects.stream()
            .map(Object::getClass)
            .collect(Collectors.toCollection(ArrayList::new));

    for (int i = 0; i < expectedTypes.length; i++) {
      if (!expectedTypes[i].isAssignableFrom(actualTypes.get(i))) {
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings("unchecked")
  private RemoteCall<T> cast(final Object invokedMethod) {
    return (RemoteCall<T>) invokedMethod;
  }
}
