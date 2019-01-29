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
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.JsonRequestFactories;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;

import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Convert;
import org.web3j.utils.Convert.Unit;

public class TransferTransaction implements Transaction<Hash> {

  private static final BigInteger MINIMUM_GAS_PRICE = BigInteger.valueOf(1000);
  private static final BigInteger TRANSFER_GAS_COST = BigInteger.valueOf(21000);

  private final Account sender;
  private final Account recipient;
  private final String amount;
  private final Unit unit;
  private final Optional<BigInteger> nonce;

  public TransferTransaction(
      final Account sender, final Account recipient, final String amount, final Unit unit) {
    this(sender, recipient, amount, unit, null);
  }

  public TransferTransaction(
      final Account sender,
      final Account recipient,
      final String amount,
      final Unit unit,
      final BigInteger nonce) {
    this.sender = sender;
    this.recipient = recipient;
    this.amount = amount;
    this.unit = unit;
    if (nonce != null) {
      this.nonce = Optional.of(nonce);
    } else {
      this.nonce = Optional.empty();
    }
  }

  @Override
  public Hash execute(final JsonRequestFactories node) {
    final String signedTransactionData = signedTransactionData();
    try {
      return Hash.fromHexString(
          node.eth().ethSendRawTransaction(signedTransactionData).send().getTransactionHash());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public String signedTransactionData() {
    final RawTransaction transaction =
        RawTransaction.createEtherTransaction(
            nonce.orElse(nonce.orElseGet(sender::getNextNonce)),
            MINIMUM_GAS_PRICE,
            TRANSFER_GAS_COST,
            recipient.getAddress(),
            Convert.toWei(amount, unit).toBigIntegerExact());

    return toHexString(TransactionEncoder.signMessage(transaction, sender.web3jCredentials()));
  }
}
