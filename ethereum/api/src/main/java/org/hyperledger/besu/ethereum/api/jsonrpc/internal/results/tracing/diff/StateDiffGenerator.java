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
 *
 */

package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.diff;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.Trace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.TracingUtils;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.tuweni.units.bigints.UInt256;

public class StateDiffGenerator {

  public Stream<Trace> generateStateDiff(final TransactionTrace transactionTrace) {
    final List<TraceFrame> traceFrames = transactionTrace.getTraceFrames();
    if (traceFrames.size() < 1) {
      return Stream.empty();
    }

    // This corresponds to the world state after the TX executed
    // It is two deep because of the way we addressed Spurious Dragon.
    final WorldUpdater transactionUpdater =
        traceFrames.get(0).getWorldUpdater().parentUpdater().get().parentUpdater().get();
    // This corresponds to the world state prior to the TX execution,
    // Either the initial block state or the state of the prior TX
    final WorldUpdater previousUpdater = transactionUpdater.parentUpdater().get();

    final StateDiffTrace stateDiffResult = new StateDiffTrace();

    for (final Account updatedAccount : transactionUpdater.getTouchedAccounts()) {
      final Address accountAddress = updatedAccount.getAddress();
      final Account rootAccount = previousUpdater.get(accountAddress);

      // calculate storage diff
      final Map<String, DiffNode> storageDiff = new TreeMap<>();
      for (final Map.Entry<UInt256, UInt256> entry :
          ((UpdateTrackingAccount<?>) updatedAccount)
              .getUpdatedStorage()
              .entrySet()) { // FIXME cast
        final UInt256 newValue = entry.getValue();
        if (rootAccount == null) {
          if (!UInt256.ZERO.equals(newValue)) {
            storageDiff.put(
                entry.getKey().toHexString(), new DiffNode(null, newValue.toHexString()));
          }
        } else {
          final UInt256 originalValue = rootAccount.getStorageValue(entry.getKey());
          if (!originalValue.equals(newValue)) {
            storageDiff.put(
                entry.getKey().toHexString(),
                new DiffNode(originalValue.toHexString(), newValue.toHexString()));
          }
        }
      }

      // populate the diff object
      final AccountDiff accountDiff =
          new AccountDiff(
              createDiffNode(rootAccount, updatedAccount, StateDiffGenerator::balanceAsHex),
              createDiffNode(rootAccount, updatedAccount, StateDiffGenerator::codeAsHex),
              createDiffNode(rootAccount, updatedAccount, StateDiffGenerator::nonceAsHex),
              storageDiff);

      if (accountDiff.hasDifference()) {
        stateDiffResult.put(accountAddress.toHexString(), accountDiff);
      }
    }

    // Add deleted accounts
    for (final Address accountAddress : transactionUpdater.getDeletedAccountAddresses()) {
      final Account deletedAccount = previousUpdater.get(accountAddress);
      if (deletedAccount == null) {
        continue;
      }
      final AccountDiff accountDiff =
          new AccountDiff(
              createDiffNode(deletedAccount, null, StateDiffGenerator::balanceAsHex),
              createDiffNode(deletedAccount, null, StateDiffGenerator::codeAsHex),
              createDiffNode(deletedAccount, null, StateDiffGenerator::nonceAsHex),
              Collections.emptyMap());
      stateDiffResult.put(accountAddress.toHexString(), accountDiff);
    }

    return Stream.of(stateDiffResult);
  }

  private DiffNode createDiffNode(
      final Account from, final Account to, final Function<Account, String> func) {
    return new DiffNode(Optional.ofNullable(from).map(func), Optional.ofNullable(to).map(func));
  }

  private static String balanceAsHex(final Account account) {
    return TracingUtils.weiAsHex(account.getBalance());
  }

  private static String codeAsHex(final Account account) {
    return account.getCode().toHexString();
  }

  private static String nonceAsHex(final Account account) {
    return "0x" + Long.toHexString(account.getNonce());
  }
}
