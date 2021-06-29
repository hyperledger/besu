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

import static org.web3j.utils.Restriction.UNRESTRICTED;

import org.hyperledger.besu.ethereum.privacy.PrivacyGroupUtil;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.besu.Besu;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.eea.crypto.PrivateTransactionEncoder;
import org.web3j.protocol.eea.crypto.RawPrivateTransaction;
import org.web3j.tx.ChainIdLong;
import org.web3j.tx.PrivateTransactionManager;
import org.web3j.tx.gas.BesuPrivacyGasProvider;
import org.web3j.utils.Base64String;
import org.web3j.utils.Numeric;

public class UnrestrictedTransactionManager extends PrivateTransactionManager {

  private final Besu besu;
  private final Credentials credentials;
  private final long chainId;
  private final Base64String privateFrom;
  private final List<Base64String> privateFor;
  private final Base64String privacyGroupId;

  public UnrestrictedTransactionManager(
      final Besu besu,
      final BesuPrivacyGasProvider gasProvider,
      final Credentials credentials,
      final long chainId,
      final Base64String privateFrom,
      final List<Base64String> privateFor,
      final Base64String privacyGroupId) {
    super(besu, gasProvider, credentials, chainId, privateFrom);
    this.besu = besu;
    this.credentials = credentials;
    this.chainId = chainId;
    this.privateFrom = privateFrom;
    this.privateFor = privateFor;
    this.privacyGroupId = privacyGroupId;
  }

  @Override
  protected Base64String getPrivacyGroupId() {
    if (privacyGroupId != null) return privacyGroupId;
    else {
      final Bytes32 privacyGroupId =
          PrivacyGroupUtil.calculateEeaPrivacyGroupId(
              Bytes.fromBase64String(privateFrom.toString()),
              privateFor.stream()
                  .map(x -> Bytes.fromBase64String(x.toString()))
                  .collect(Collectors.toList()));

      return Base64String.wrap(privacyGroupId.toBase64String());
    }
  }

  @Override
  protected Object privacyGroupIdOrPrivateFor() {
    if (privacyGroupId != null) return privacyGroupId;
    else return privateFor;
  }

  @SuppressWarnings("unchecked")
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
        besu.privGetTransactionCount(credentials.getAddress(), getPrivacyGroupId())
            .send()
            .getTransactionCount();

    final RawPrivateTransaction transaction;
    if (privacyGroupId != null) {
      transaction =
          RawPrivateTransaction.createTransaction(
              nonce, gasPrice, gasLimit, to, data, privateFrom, privacyGroupId, UNRESTRICTED);
    } else {
      transaction =
          RawPrivateTransaction.createTransaction(
              nonce, gasPrice, gasLimit, to, data, privateFrom, privateFor, UNRESTRICTED);
    }

    return signAndSend(transaction);
  }

  public EthSendTransaction signAndSend(final RawPrivateTransaction rawTransaction)
      throws IOException {
    final String hexValue = sign(rawTransaction);
    return this.besu.eeaSendRawTransaction(hexValue).send();
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
}
