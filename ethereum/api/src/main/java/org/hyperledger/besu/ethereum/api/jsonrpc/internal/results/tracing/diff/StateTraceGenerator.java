/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.diff;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.TracingUtils;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.tracing.TraceFrame;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.tuweni.units.bigints.UInt256;

/**
 * Generates state diffs (before/after account and storage changes) for Ethereum transactions.
 *
 * <p>This class is used in JSON-RPC tracing to show how accounts and storage values were modified
 * by a transaction. It can produce:
 *
 * <ul>
 *   <li>A diff-only trace (changed values only)
 *   <li>A pre-state trace (complete state snapshot of relevant accounts)
 * </ul>
 */
public class StateTraceGenerator {

  public StateTraceGenerator() {}

  /**
   * Generate a state diff (only differences, not the full pre-state) for a transaction trace.
   *
   * @param transactionTrace The transaction trace containing execution and world state data.
   * @return A stream of {@link StateDiffTrace} objects representing modified state.
   */
  public Stream<StateDiffTrace> generateStateDiff(final TransactionTrace transactionTrace) {
    return generate(transactionTrace, false); // false = diff-only (not pre-state)
  }

  /**
   * Generate the full pre-state for a transaction.
   *
   * <p>Unlike {@link #generateStateDiff(TransactionTrace)}, this method does not only include
   * modified accounts. It also ensures that relevant accounts such as the sender, recipient, and
   * coinbase are included in the result, even if they were not modified by the transaction.
   *
   * @param transactionTrace The transaction trace containing execution and world state data.
   * @return A stream of {@link StateDiffTrace} objects representing the pre-state.
   */
  public Stream<StateDiffTrace> generatePreState(final TransactionTrace transactionTrace) {
    return generate(transactionTrace, true); // true = include full pre-state
  }

  /**
   * Internal generator for state diffs or pre-states depending on the mode.
   *
   * @param transactionTrace The transaction trace.
   * @param isPreState Whether to generate a pre-state (true) or diff (false).
   * @return A stream containing one {@link StateDiffTrace}.
   */
  private Stream<StateDiffTrace> generate(
      final TransactionTrace transactionTrace, final boolean isPreState) {
    final List<TraceFrame> traceFrames = transactionTrace.getTraceFrames();
    if (traceFrames.isEmpty()) {
      return Stream.empty();
    }

    // Get the world state: transactionUpdater is post-transaction, previousUpdater is
    // pre-transaction
    final WorldUpdater transactionUpdater = getTransactionUpdater(traceFrames);
    final WorldUpdater previousUpdater = transactionUpdater.parentUpdater().get();

    final StateDiffTrace stateDiffResult = new StateDiffTrace();

    // Compare modified accounts (existing or new accounts touched by the tx)
    getTouchedAccounts(transactionUpdater, transactionTrace)
        .forEach(
            updatedAccount -> {
              final Account rootAccount = previousUpdater.get(updatedAccount.getAddress());
              processAccountDiff(stateDiffResult, updatedAccount, rootAccount, isPreState);
            });

    processDeletedAccounts(stateDiffResult, transactionUpdater, previousUpdater);



    return Stream.of(stateDiffResult);
  }

  /**
   * Get the {@link WorldUpdater} at the transaction scope.
   *
   * <p>Traverses up the world updater chain to retrieve the correct layer.
   */
  private WorldUpdater getTransactionUpdater(final List<TraceFrame> traceFrames) {
    // Transaction updater sits two levels above the frame's updater
    return traceFrames.getFirst().getWorldUpdater().parentUpdater().get().parentUpdater().get();
  }

  /** Use block access list if present to get touched accounts. */
  private Collection<? extends Account> getTouchedAccounts(
      final WorldUpdater transactionUpdater, final TransactionTrace transactionTrace) {
    if (transactionTrace.getAccessListTracker().isPresent()) {
      List<Account> touchedAccounts = new java.util.ArrayList<>();
      for (Address address : transactionTrace.getAccessListTracker().get().getTouchedAccounts()) {
        var account = transactionUpdater.get(address);
        if (account != null) {
          touchedAccounts.add(transactionUpdater.get(address));
        }
      }
      return touchedAccounts;
    } else {
      return transactionUpdater.getTouchedAccounts();
    }
  }

  /**
   * Process an account that was touched during the transaction and record its state difference
   * compared to the pre-transaction state.
   *
   * <p>Behavior differs depending on mode:
   *
   * <ul>
   *   <li><b>Diff mode</b> (isPreState = false): Only accounts with at least one modified field
   *       (including storage) are included.
   *   <li><b>Pre-state mode</b> (isPreState = true): The account is included even if no fields were
   *       modified, showing the “before” values of touched accounts.
   * </ul>
   *
   * @param stateDiff The aggregated diff being built for this transaction.
   * @param updatedAccount The account state after transaction execution.
   * @param rootAccount The account state before transaction execution, or {@code null} if the
   *     account was newly created.
   * @param isPreState Whether pre-state mode is enabled.
   */
  private void processAccountDiff(
      final StateDiffTrace stateDiff,
      final Account updatedAccount,
      final Account rootAccount,
      final boolean isPreState) {

    final Address accountAddress = updatedAccount.getAddress();

    // Compute storage differences for this account
    final Map<String, DiffNode> storageDiff =
        calculateStorageDiff((MutableAccount) updatedAccount, rootAccount, isPreState);

    // Build account-level diff
    final AccountDiff accountDiff =
        new AccountDiff(
            createDiffNode(rootAccount, updatedAccount, StateTraceGenerator::balanceAsHex),
            createDiffNode(rootAccount, updatedAccount, StateTraceGenerator::codeAsHex),
            createDiffNode(rootAccount, updatedAccount, StateTraceGenerator::codeHashAsHex),
            createDiffNode(rootAccount, updatedAccount, StateTraceGenerator::nonceAsHex),
            storageDiff);

    // Record only if something changed (or always if pre-state)
    if (isPreState || accountDiff.hasDifference()) {
      stateDiff.put(accountAddress.toHexString(), accountDiff);
    }
  }

  /**
   * Calculate the storage differences for an account between the pre-transaction and
   * post-transaction world states.
   *
   * <p>Behavior differs based on the mode:
   *
   * <ul>
   *   <li><b>Diff mode</b> (isPreState = false): Only includes storage slots whose values changed
   *       compared to the pre-transaction state. New accounts will only include non-zero storage
   *       slots.
   *   <li><b>Pre-state mode</b> (isPreState = true): Includes all storage slots that were touched,
   *       even if the value did not change. New accounts will include both zero and non-zero
   *       values.
   * </ul>
   *
   * @param updatedAccount The account state after transaction execution (mutable).
   * @param rootAccount The account state before the transaction, or {@code null} if the account was
   *     newly created during the transaction.
   * @param isPreState Whether pre-state mode is active.
   * @return A map of storage slot key → {@link DiffNode}, representing the diff of each slot.
   */
  private Map<String, DiffNode> calculateStorageDiff(
      final MutableAccount updatedAccount, final Account rootAccount, final boolean isPreState) {

    final Map<String, DiffNode> storageDiff = new TreeMap<>();
    // Iterate through all updated storage slots in this transaction
    updatedAccount
        .getUpdatedStorage()
        .forEach(
            (key, newValue) -> {
              final String keyHex = key.toHexString();
              final String newValueHex = newValue.toHexString();

              if (rootAccount == null) {
                // Case 1: Account did not exist before this transaction
                // Record non-zero slots in diff mode, or all slots in pre-state mode
                if (!UInt256.ZERO.equals(newValue) || isPreState) {
                  storageDiff.put(keyHex, new DiffNode(null, newValueHex));
                }
              } else {
                // Case 2: Account existed pre-transaction
                final String originalValueHex = rootAccount.getStorageValue(key).toHexString();
                // Record differences, or everything if pre-state mode is enabled
                if (!originalValueHex.equals(newValueHex) || isPreState) {
                  storageDiff.put(keyHex, new DiffNode(originalValueHex, newValueHex));
                }
              }
            });
    return storageDiff;
  }

  /** Record accounts deleted during the transaction. */
  private void processDeletedAccounts(
      final StateDiffTrace stateDiff,
      final WorldUpdater transactionUpdater,
      final WorldUpdater previousUpdater) {

    transactionUpdater
        .getDeletedAccountAddresses()
        .forEach(
            accountAddress -> {
              final Account deletedAccount = previousUpdater.get(accountAddress);
              if (deletedAccount != null) {
                final AccountDiff accountDiff =
                    new AccountDiff(
                        createDiffNode(deletedAccount, null, StateTraceGenerator::balanceAsHex),
                        createDiffNode(deletedAccount, null, StateTraceGenerator::codeAsHex),
                        createDiffNode(deletedAccount, null, StateTraceGenerator::codeHashAsHex),
                        createDiffNode(deletedAccount, null, StateTraceGenerator::nonceAsHex),
                        Collections.emptyMap());
                stateDiff.put(accountAddress.toHexString(), accountDiff);
              }
            });
  }

  /** Create a {@link DiffNode} by extracting a field from two account states. */
  private DiffNode createDiffNode(
      final Account from, final Account to, final Function<Account, String> func) {

    return new DiffNode(Optional.ofNullable(from).map(func), Optional.ofNullable(to).map(func));
  }

  private static String balanceAsHex(final Account account) {
    return TracingUtils.weiAsHex(account.getBalance());
  }

  private static String codeHashAsHex(final Account account) {
    return account.getCodeHash().toHexString();
  }

  private static String codeAsHex(final Account account) {
    return account.getCode().toHexString();
  }

  private static String nonceAsHex(final Account account) {
    return "0x" + Long.toHexString(account.getNonce());
  }
}
