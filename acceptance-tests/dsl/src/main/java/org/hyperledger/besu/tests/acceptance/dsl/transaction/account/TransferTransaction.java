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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.account;

import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.SignUtil;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.TransactionWithSignatureAlgorithm;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Convert;
import org.web3j.utils.Convert.Unit;
import org.web3j.utils.Numeric;

public class TransferTransaction
    implements Transaction<Hash>, TransactionWithSignatureAlgorithm<Hash> {

  /** Price for each for each GAS units in this transaction (wei). */
  private static final BigInteger MINIMUM_GAS_PRICE = BigInteger.valueOf(1000);

  /** Number of GAS units that the transaction will cost. */
  private static final BigInteger INTRINSIC_GAS = BigInteger.valueOf(21000);

  private final Account sender;
  private final Account recipient;
  private final BigDecimal transferAmount;
  private final Unit transferUnit;
  private final BigInteger gasPrice;
  private final BigInteger nonce;

  public TransferTransaction(
      final Account sender,
      final Account recipient,
      final Amount transferAmount,
      final Amount gasPrice,
      final BigInteger nonce) {
    this.sender = sender;
    this.recipient = recipient;
    this.transferAmount = transferAmount.getValue();
    this.transferUnit = transferAmount.getUnit();
    this.gasPrice = gasPrice == null ? MINIMUM_GAS_PRICE : convertGasPriceToWei(gasPrice);
    this.nonce = nonce;
  }

  @Override
  public Hash execute(final NodeRequests node) {
    final String signedTransactionData = signedTransactionData();
    return sendRawTransaction(node, signedTransactionData);
  }

  @Override
  public Hash execute(final NodeRequests node, final SignatureAlgorithm signatureAlgorithm) {
    final String signedTransactionData =
        signedTransactionDataWithSignatureAlgorithm(signatureAlgorithm);
    return sendRawTransaction(node, signedTransactionData);
  }

  public Amount executionCost() {
    return Amount.wei(INTRINSIC_GAS.multiply(gasPrice));
  }

  public String signedTransactionData() {
    final RawTransaction transaction = createRawTransaction();

    return Numeric.toHexString(
        TransactionEncoder.signMessage(transaction, sender.web3jCredentialsOrThrow()));
  }

  private String signedTransactionDataWithSignatureAlgorithm(
      final SignatureAlgorithm signatureAlgorithm) {
    return SignUtil.signTransaction(createRawTransaction(), sender, signatureAlgorithm);
  }

  private Hash sendRawTransaction(final NodeRequests node, final String signedTransactionData) {
    try {
      return Hash.fromHexString(
          node.eth().ethSendRawTransaction(signedTransactionData).send().getTransactionHash());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Optional<BigInteger> getNonce() {
    return nonce == null ? Optional.empty() : Optional.of(nonce);
  }

  private BigInteger convertGasPriceToWei(final Amount unconverted) {
    final BigInteger price =
        Convert.toWei(unconverted.getValue(), unconverted.getUnit()).toBigInteger();

    if (MINIMUM_GAS_PRICE.compareTo(price) > 0) {
      throw new IllegalArgumentException(
          String.format(
              "Gas price: %s WEI, is below the accepted minimum: %s WEI",
              price, MINIMUM_GAS_PRICE));
    }

    return price;
  }

  private RawTransaction createRawTransaction() {
    final Optional<BigInteger> nonce = getNonce();

    return RawTransaction.createEtherTransaction(
        nonce.orElse(nonce.orElseGet(sender::getNextNonce)),
        gasPrice,
        INTRINSIC_GAS,
        recipient.getAddress(),
        Convert.toWei(transferAmount, transferUnit).toBigIntegerExact());
  }
}
