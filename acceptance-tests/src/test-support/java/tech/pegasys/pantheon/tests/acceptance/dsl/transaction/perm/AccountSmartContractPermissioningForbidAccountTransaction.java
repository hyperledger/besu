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
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction.perm;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.web3j.utils.Numeric.toHexString;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.tests.acceptance.dsl.account.Account;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.NodeRequests;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import java.io.IOException;
import java.math.BigInteger;

import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;

public class AccountSmartContractPermissioningForbidAccountTransaction
    implements Transaction<Hash> {

  private static final BytesValue REMOVE_ACCOUNT_SIGNATURE =
      tech.pegasys.pantheon.crypto.Hash.keccak256(
              BytesValue.of("removeAccount(address)".getBytes(UTF_8)))
          .slice(0, 4);

  private final Account sender;
  private final Address contractAddress;
  private final Address account;

  public AccountSmartContractPermissioningForbidAccountTransaction(
      final Account sender, final Address contractAddress, final Address account) {
    this.sender = sender;
    this.contractAddress = contractAddress;
    this.account = account;
  }

  @Override
  public Hash execute(final NodeRequests node) {
    final String signedTransactionData = signedTransactionData();
    try {
      String hash =
          node.eth().ethSendRawTransaction(signedTransactionData).send().getTransactionHash();
      return Hash.fromHexString(hash);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String signedTransactionData() {
    final BytesValue payload =
        BytesValues.concatenate(
            REMOVE_ACCOUNT_SIGNATURE,
            BytesValue.fromHexString("0x000000000000000000000000"),
            account);

    RawTransaction transaction =
        RawTransaction.createTransaction(
            sender.getNextNonce(),
            BigInteger.valueOf(1000),
            BigInteger.valueOf(100_000),
            contractAddress.toString(),
            payload.toString());

    return toHexString(TransactionEncoder.signMessage(transaction, sender.web3jCredentials()));
  }
}
