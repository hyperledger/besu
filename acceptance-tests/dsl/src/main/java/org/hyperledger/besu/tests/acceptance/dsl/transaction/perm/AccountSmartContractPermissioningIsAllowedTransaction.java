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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.perm;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;

import org.apache.tuweni.bytes.Bytes;
import org.web3j.protocol.core.DefaultBlockParameterName;

public class AccountSmartContractPermissioningIsAllowedTransaction implements Transaction<Boolean> {

  private static final Bytes IS_ACCOUNT_ALLOWED_SIGNATURE =
      Hash.keccak256(Bytes.of("whitelistContains(address)".getBytes(UTF_8))).slice(0, 4);

  private final Address contractAddress;
  private final Address account;

  public AccountSmartContractPermissioningIsAllowedTransaction(
      final Address contractAddress, final Address account) {
    this.contractAddress = contractAddress;
    this.account = account;
  }

  @Override
  public Boolean execute(final NodeRequests node) {
    try {
      final String value =
          node.eth().ethCall(payload(), DefaultBlockParameterName.LATEST).send().getValue();
      return checkTransactionResult(Bytes.fromHexString(value));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  // Checks the returned bytes from the permissioning contract call to see if it's a value we
  // understand
  static Boolean checkTransactionResult(final Bytes result) {
    // booleans are padded to 32 bytes
    if (result.size() != 32) {
      throw new IllegalArgumentException("Unexpected result size");
    }

    // 0 is false
    if (result.equals(
        Bytes.fromHexString(
            "0x0000000000000000000000000000000000000000000000000000000000000000"))) {
      return false;
      // 1 filled to 32 bytes is true
    } else if (result.equals(
        Bytes.fromHexString(
            "0x0000000000000000000000000000000000000000000000000000000000000001"))) {
      return true;
      // Anything else is wrong
    } else {
      throw new IllegalStateException("Unexpected result form");
    }
  }

  private org.web3j.protocol.core.methods.request.Transaction payload() {
    final Bytes payload =
        Bytes.concatenate(
            IS_ACCOUNT_ALLOWED_SIGNATURE,
            Bytes.fromHexString("0x000000000000000000000000"),
            account);

    return org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
        null, null, null, null, contractAddress.toString(), payload.toString());
  }
}
