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
package org.hyperledger.besu.tests.acceptance.dsl.privacy.contract;

import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.tx.Contract;
import org.web3j.tx.LegacyPrivateTransactionManager;
import org.web3j.tx.PrivateTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.BesuPrivacyGasProvider;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.utils.Base64String;

public class DeployPrivateSmartContractTransaction<T extends Contract> implements Transaction<T> {

  private static final BesuPrivacyGasProvider GAS_PROVIDER =
      new BesuPrivacyGasProvider(BigInteger.valueOf(1000));
  private static final Object METHOD_IS_STATIC = null;

  private final Class<T> clazz;
  private final Credentials senderCredentials;
  private final long chainId;
  private final Base64String privateFrom;
  private final List<Base64String> privateFor;
  private Object[] params;

  public DeployPrivateSmartContractTransaction(
      final Class<T> clazz,
      final String transactionSigningKey,
      final long chainId,
      final String privateFrom,
      final List<String> privateFor) {
    this.clazz = clazz;
    this.senderCredentials = Credentials.create(transactionSigningKey);
    this.chainId = chainId;
    this.privateFrom = Base64String.wrap(privateFrom);
    this.privateFor = Base64String.wrapList(privateFor);
  }

  public DeployPrivateSmartContractTransaction(
      final Class<T> clazz,
      final String transactionSigningKey,
      final long chainId,
      final String privateFrom,
      final List<String> privateFor,
      final Object... params) {
    this.clazz = clazz;
    this.senderCredentials = Credentials.create(transactionSigningKey);
    this.chainId = chainId;
    this.privateFrom = Base64String.wrap(privateFrom);
    this.privateFor = Base64String.wrapList(privateFor);
    this.params = params;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public T execute(final NodeRequests node) {
    final PrivateTransactionManager privateTransactionManager =
        new LegacyPrivateTransactionManager(
            node.privacy().getBesuClient(),
            GAS_PROVIDER,
            senderCredentials,
            chainId,
            privateFrom,
            privateFor,
            15,
            1000);
    try {
      if (params != null) {
        ArrayList<Class> paramClasses =
            new ArrayList<>(
                Arrays.asList(Web3j.class, TransactionManager.class, ContractGasProvider.class));
        paramClasses.addAll(
            Arrays.stream(params).map(Object::getClass).collect(Collectors.toList()));

        ArrayList<Object> allParams =
            new ArrayList<>(
                Arrays.asList(
                    node.privacy().getBesuClient(), privateTransactionManager, GAS_PROVIDER));
        allParams.addAll(Arrays.asList(params));

        final Method method =
            clazz.getMethod("deploy", paramClasses.toArray(new Class[paramClasses.size()]));

        final Object invoked = method.invoke(METHOD_IS_STATIC, allParams.toArray());

        return cast(invoked).send();
      } else {
        final Method method =
            clazz.getMethod(
                "deploy", Web3j.class, TransactionManager.class, ContractGasProvider.class);
        final Object invoked =
            method.invoke(
                METHOD_IS_STATIC,
                node.privacy().getBesuClient(),
                privateTransactionManager,
                GAS_PROVIDER);

        return cast(invoked).send();
      }
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private RemoteCall<T> cast(final Object invokedMethod) {
    return (RemoteCall<T>) invokedMethod;
  }
}
