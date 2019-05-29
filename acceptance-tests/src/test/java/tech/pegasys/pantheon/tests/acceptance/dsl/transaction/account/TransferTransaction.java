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
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction.account;

import static org.web3j.utils.Numeric.toHexString;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.tests.acceptance.dsl.account.Account;
import tech.pegasys.pantheon.tests.acceptance.dsl.blockchain.Amount;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.NodeRequests;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;

import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Convert;
import org.web3j.utils.Convert.Unit;

public class TransferTransaction implements Transaction<Hash> {

  /** Price for each for each GAS units in this transaction (wei). */
  private static final BigInteger MINIMUM_GAS_PRICE = BigInteger.valueOf(1000);

  /** Number of GAS units that the transaction will cost. */
  private static final BigInteger INTRINSIC_GAS = BigInteger.valueOf(21000);

  private final Account sender;
  private final Account recipient;
  private final String transferAmount;
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
    try {
      return Hash.fromHexString(
          node.eth().ethSendRawTransaction(signedTransactionData).send().getTransactionHash());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Amount executionCost() {
    return Amount.wei(INTRINSIC_GAS.multiply(gasPrice));
  }

  public String signedTransactionData() {
    final Optional<BigInteger> nonce = getNonce();

    final RawTransaction transaction =
        RawTransaction.createEtherTransaction(
            nonce.orElse(nonce.orElseGet(sender::getNextNonce)),
            gasPrice,
            INTRINSIC_GAS,
            recipient.getAddress(),
            Convert.toWei(transferAmount, transferUnit).toBigIntegerExact());

    return toHexString(TransactionEncoder.signMessage(transaction, sender.web3jCredentials()));
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
}
