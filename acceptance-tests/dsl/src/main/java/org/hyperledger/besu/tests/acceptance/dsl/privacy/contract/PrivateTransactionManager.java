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

import static org.web3j.utils.Restriction.RESTRICTED;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.besu.Besu;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetCode;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.eea.crypto.PrivateTransactionEncoder;
import org.web3j.protocol.eea.crypto.RawPrivateTransaction;
import org.web3j.tx.ChainIdLong;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.response.PollingPrivateTransactionReceiptProcessor;
import org.web3j.tx.response.TransactionReceiptProcessor;
import org.web3j.utils.Base64String;
import org.web3j.utils.Numeric;
import org.web3j.utils.PrivacyGroupUtils;
import org.web3j.utils.Restriction;

public class PrivateTransactionManager extends TransactionManager {

  private final Besu besu;

  private final Credentials credentials;
  private final long chainId;

  private final Base64String privateFrom;

  private final List<Base64String> privateFor;
  private final Base64String privacyGroupId;

  private final Restriction restriction;

  public PrivateTransactionManager(
      final Besu besu,
      final Credentials credentials,
      final TransactionReceiptProcessor transactionReceiptProcessor,
      final long chainId,
      final Base64String privateFrom,
      final Base64String privacyGroupId,
      final Restriction restriction) {
    super(transactionReceiptProcessor, credentials.getAddress());
    this.besu = besu;
    this.credentials = credentials;
    this.chainId = chainId;
    this.privateFrom = privateFrom;
    this.privateFor = null;
    this.privacyGroupId = privacyGroupId;
    this.restriction = restriction;
  }

  public PrivateTransactionManager(
      final Besu besu,
      final Credentials credentials,
      final TransactionReceiptProcessor transactionReceiptProcessor,
      final long chainId,
      final Base64String privateFrom,
      final List<Base64String> privateFor,
      final Restriction restriction) {
    super(transactionReceiptProcessor, credentials.getAddress());
    this.besu = besu;
    this.credentials = credentials;
    this.chainId = chainId;
    this.privateFrom = privateFrom;
    this.privateFor = privateFor;
    this.privacyGroupId = PrivacyGroupUtils.generateLegacyGroup(privateFrom, privateFor);
    this.restriction = restriction;
  }

  public static class Builder {
    private final Besu besu;
    private final Credentials credentials;
    private long chainId;
    private final Base64String privateFrom;
    private Base64String privacyGroupId;
    private TransactionReceiptProcessor transactionReceiptProcessor;
    private Restriction restriction = RESTRICTED;
    private List<Base64String> privateFor = null;

    public Builder(final Besu besu, final Credentials credentials, final Base64String privateFrom) {
      this.besu = besu;
      this.credentials = credentials;
      this.chainId = ChainIdLong.NONE;
      this.privateFrom = privateFrom;
      transactionReceiptProcessor =
          new PollingPrivateTransactionReceiptProcessor(
              besu, DEFAULT_POLLING_FREQUENCY, DEFAULT_POLLING_ATTEMPTS_PER_TX_HASH);
    }

    public Builder setRestriction(final Restriction restriction) {
      this.restriction = restriction;
      return this;
    }

    public Builder setTransactionReceiptProcessor(
        final TransactionReceiptProcessor transactionReceiptProcessor) {
      this.transactionReceiptProcessor = transactionReceiptProcessor;
      return this;
    }

    public Builder setPrivacyGroupId(final Base64String privacyGroupId) {
      this.privacyGroupId = privacyGroupId;
      return this;
    }

    public Builder setPrivateFor(final List<Base64String> privateFor) {
      this.privateFor = privateFor;
      return this;
    }

    public Builder setChainId(final long chainId) {
      this.chainId = chainId;
      return this;
    }

    public PrivateTransactionManager build() {
      if (privateFor != null) {
        return new PrivateTransactionManager(
            besu,
            credentials,
            transactionReceiptProcessor,
            chainId,
            privateFrom,
            privateFor,
            restriction);
      } else {
        return new PrivateTransactionManager(
            besu,
            credentials,
            transactionReceiptProcessor,
            chainId,
            privateFrom,
            privacyGroupId,
            restriction);
      }
    }
  }

  @Override
  public EthSendTransaction sendTransaction(
      final BigInteger gasPrice,
      final BigInteger gasLimit,
      final String to,
      final String data,
      final BigInteger value,
      final boolean constructor)
      throws IOException {

    final BigInteger nonce =
        besu.privGetTransactionCount(credentials.getAddress(), privacyGroupId)
            .send()
            .getTransactionCount();

    final RawPrivateTransaction transaction;

    if (privateFor != null) {
      transaction =
          RawPrivateTransaction.createTransaction(
              nonce, gasPrice, gasLimit, to, data, privateFrom, privateFor, restriction);
    } else {
      transaction =
          RawPrivateTransaction.createTransaction(
              nonce, gasPrice, gasLimit, to, data, privateFrom, privacyGroupId, restriction);
    }

    return signAndSend(transaction);
  }

  @Override
  public String sendCall(
      final String to, final String data, final DefaultBlockParameter defaultBlockParameter)
      throws IOException {
    final EthCall ethCall =
        besu.privCall(
                Transaction.createEthCallTransaction(getFromAddress(), to, data),
                defaultBlockParameter,
                privacyGroupId.toString())
            .send();

    return ethCall.getValue();
  }

  @Override
  public EthGetCode getCode(
      final String contractAddress, final DefaultBlockParameter defaultBlockParameter)
      throws IOException {
    return this.besu
        .privGetCode(privacyGroupId.toString(), contractAddress, defaultBlockParameter)
        .send();
  }

  public String sign(final RawPrivateTransaction rawTransaction) {

    final byte[] signedMessage;

    if (chainId > ChainIdLong.NONE) {
      signedMessage = PrivateTransactionEncoder.signMessage(rawTransaction, chainId, credentials);
    } else {
      signedMessage = PrivateTransactionEncoder.signMessage(rawTransaction, credentials);
    }

    return Numeric.toHexString(signedMessage);
  }

  public EthSendTransaction signAndSend(final RawPrivateTransaction rawTransaction)
      throws IOException {
    final String hexValue = sign(rawTransaction);
    return this.besu.eeaSendRawTransaction(hexValue).send();
  }
}
