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

import java.io.IOException;
import java.math.BigInteger;

import org.web3j.crypto.Credentials;
import org.web3j.tx.PrivateTransactionManager;
import org.web3j.tx.gas.BesuPrivacyGasProvider;
import org.web3j.utils.Base64String;
import org.web3j.utils.Restriction;

public class CallOnchainPermissioningPrivateSmartContractFunction implements Transaction<String> {

  private static final BesuPrivacyGasProvider GAS_PROVIDER =
      new BesuPrivacyGasProvider(BigInteger.valueOf(1000));
  private final String contractAddress;
  private final String encodedFunction;
  private final Credentials senderCredentials;
  private final Base64String privateFrom;
  private final Base64String privacyGroupId;

  public CallOnchainPermissioningPrivateSmartContractFunction(
      final String contractAddress,
      final String encodedFunction,
      final String transactionSigningKey,
      final String privateFrom,
      final String privacyGroupId) {

    this.contractAddress = contractAddress;
    this.encodedFunction = encodedFunction;
    this.senderCredentials = Credentials.create(transactionSigningKey);
    this.privateFrom = Base64String.wrap(privateFrom);
    this.privacyGroupId = Base64String.wrap(privacyGroupId);
  }

  @Override
  public String execute(final NodeRequests node) {
    final PrivateTransactionManager privateTransactionManager =
        node.privacy()
            .getTransactionManager(
                senderCredentials, privateFrom, privacyGroupId, Restriction.RESTRICTED);

    try {
      return privateTransactionManager
          .sendTransaction(
              GAS_PROVIDER.getGasPrice(),
              GAS_PROVIDER.getGasLimit(),
              contractAddress,
              encodedFunction,
              null)
          .getTransactionHash();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
