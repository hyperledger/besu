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
package tech.pegasys.pantheon.tests.acceptance.dsl.privacy.contract;

import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.NodeRequests;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.pantheon.Pantheon;
import org.web3j.tx.LegacyPrivateTransactionManager;
import org.web3j.tx.PrivateTransactionManager;
import org.web3j.tx.gas.PantheonPrivacyGasProvider;
import org.web3j.utils.Base64String;

public class CallPrivateSmartContractFunction implements Transaction<String> {

  private static final PantheonPrivacyGasProvider GAS_PROVIDER =
      new PantheonPrivacyGasProvider(BigInteger.valueOf(1000));
  private final String contractAddress;
  private final String encodedFunction;
  private final Credentials senderCredentials;
  private final long chainId;
  private final Base64String privateFrom;
  private final List<Base64String> privateFor;

  public CallPrivateSmartContractFunction(
      final String contractAddress,
      final String encodedFunction,
      final String transactionSigningKey,
      final long chainId,
      final String privateFrom,
      final List<String> privateFor) {

    this.contractAddress = contractAddress;
    this.encodedFunction = encodedFunction;
    this.senderCredentials = Credentials.create(transactionSigningKey);
    this.chainId = chainId;
    this.privateFrom = Base64String.wrap(privateFrom);
    this.privateFor = Base64String.wrapList(privateFor);
  }

  @Override
  public String execute(final NodeRequests node) {
    final Pantheon pantheon = node.privacy().getPantheonClient();

    final PrivateTransactionManager privateTransactionManager =
        new LegacyPrivateTransactionManager(
            pantheon, GAS_PROVIDER, senderCredentials, chainId, privateFrom, privateFor);

    try {
      return privateTransactionManager
          .sendTransaction(
              GAS_PROVIDER.getGasPrice(),
              GAS_PROVIDER.getGasLimit(),
              contractAddress,
              encodedFunction,
              null)
          .getTransactionHash();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
