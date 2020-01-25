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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.Trace;
import org.hyperledger.besu.ethereum.core.AbstractWorldUpdater;
import org.hyperledger.besu.ethereum.core.AbstractWorldUpdater.UpdateTrackingAccount;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.debug.TraceFrame;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.tuweni.units.bigints.UInt256;

public class StateDiffGenerator {

  private final Map<Address, Map<UInt256, UInt256>> midBlockStorageState = new TreeMap<>();
  private final Map<Address, Wei> midBlockAccountBalance = new TreeMap<>();

  public Stream<Trace> generateStateDiff(final TransactionTrace transactionTrace) {
    List<TraceFrame> traceFrames = transactionTrace.getTraceFrames();
    if (traceFrames.size() < 1) {
      return Stream.empty();
    }

    WorldUpdater updatedTx =
        traceFrames.get(0).getMessageFrame().getWorldState().parentUpdater().get();
    WorldUpdater contextTx = updatedTx.parentUpdater().get();

    StateDiffTrace stateDiffResult = new StateDiffTrace();

    for (Account touchedAccount : updatedTx.getTouchedAccounts()) {
      if (!(touchedAccount instanceof AbstractWorldUpdater.UpdateTrackingAccount)) {
        continue;
      }
      Address accountAddress = touchedAccount.getAddress();
      UpdateTrackingAccount<?> updatedAccount =
          (UpdateTrackingAccount<?>) updatedTx.get(accountAddress);
      Account rootAccount = ((UpdateTrackingAccount<?>) touchedAccount).getWrappedAccount();
      Map<String, DiffNode> storageDiff = new TreeMap<>();

      Wei originalBalance =
          midBlockAccountBalance.computeIfAbsent(
              accountAddress, addr -> (rootAccount == null) ? null : rootAccount.getBalance());
      Wei newBalance = updatedAccount.getBalance();
      midBlockAccountBalance.put(accountAddress, newBalance);

      Map<UInt256, UInt256> midBlockAccountStorage =
          midBlockStorageState.computeIfAbsent(accountAddress, address -> new TreeMap<>());
      for (Map.Entry<UInt256, UInt256> entry : updatedAccount.getUpdatedStorage().entrySet()) {
        UInt256 originalValue =
            midBlockAccountStorage.computeIfAbsent(
                entry.getKey(), key -> updatedAccount.getOriginalStorageValue(entry.getKey()));
        UInt256 newValue = entry.getValue();
        midBlockAccountStorage.put(entry.getKey(), newValue);
        storageDiff.put(
            entry.getKey().toHexString(),
            new DiffNode(originalValue.toHexString(), newValue.toHexString()));
      }

      AccountDiff accountDiff =
          new AccountDiff(
              new DiffNode(
                  Optional.ofNullable(originalBalance)
                      .map(Wei::toShortHexString)
                      .map(StateDiffGenerator::toQuantityShortHex),
                  Optional.ofNullable(newBalance)
                      .map(Wei::toShortHexString)
                      .map(StateDiffGenerator::toQuantityShortHex)),
              updatedAccount.codeWasUpdated()
                  ? createDiffNode(
                      rootAccount, updatedAccount, account -> account.getCode().toHexString())
                  : DiffNode.EQUAL,
              createDiffNode(
                  rootAccount,
                  updatedAccount,
                  account -> "0x" + Long.toHexString(account.getNonce())),
              storageDiff);

      if (accountDiff.hasDifference()) {
        stateDiffResult.put(accountAddress.toHexString(), accountDiff);
      } else {
        System.out.println("no diff " + accountAddress);
      }
    }

    for (Address accountAddress : updatedTx.getDeletedAccountAddresses()) {
      Account rootAccount = contextTx.get(accountAddress);
      AccountDiff accountDiff =
          new AccountDiff(
              createDiffNode(
                  rootAccount,
                  null,
                  account -> toQuantityShortHex(account.getBalance().toShortHexString())),
              createDiffNode(rootAccount, null, account -> account.getCode().toHexString()),
              createDiffNode(
                  rootAccount, null, account -> "0x" + Long.toHexString(account.getNonce())),
              Collections.emptyMap());
      stateDiffResult.put(accountAddress.toHexString(), accountDiff);
    }

    return Stream.of(stateDiffResult);
  }

  DiffNode createDiffNode(
      final Account from, final Account to, final Function<Account, String> func) {
    return new DiffNode(Optional.ofNullable(from).map(func), Optional.ofNullable(to).map(func));
  }

  private static String toQuantityShortHex(final String hex) {
    // Skipping '0x'
    return "0x".equals(hex) ? "0x0" : hex;
  }
}
