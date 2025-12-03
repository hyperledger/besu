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
package org.hyperledger.besu.ethereum.mainnet.block.access.list;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class BalPartialViewValidator {

  private BalPartialViewValidator() {}

  // TODO: Check not only pv subset of BAL, but full equality
  public static boolean validateTxAccesses(
      final PartialBlockAccessView view, final BlockAccessList blockBal) {

    final Map<Address, BlockAccessList.AccountChanges> changesByAddress =
        blockBal.accountChanges().stream()
            .collect(Collectors.toMap(BlockAccessList.AccountChanges::address, ac -> ac));

    final int txIndex = view.getTxIndex();

    for (final var accView : view.accountChanges()) {
      final Address addr = accView.getAddress();
      final BlockAccessList.AccountChanges balAcc = changesByAddress.get(addr);
      if (balAcc == null) {
        return false;
      }

      if (accView.getPostBalance().isPresent()) {
        final Wei viewBalance = accView.getPostBalance().orElseThrow();
        final boolean balanceMatch =
            balAcc.balanceChanges().stream()
                .anyMatch(
                    balChange ->
                        balChange.txIndex() == txIndex && viewBalance.equals(balChange.postBalance()));
        if (!balanceMatch) {
          return false;
        }
      }

      if (accView.getNonceChange().isPresent()) {
        final long viewNonce = accView.getNonceChange().orElseThrow();
        final boolean nonceMatch =
            balAcc.nonceChanges().stream()
                .anyMatch(balChange -> balChange.txIndex() == txIndex && balChange.newNonce() == viewNonce);
        if (!nonceMatch) {
          return false;
        }
      }

      if (accView.getNewCode().isPresent()) {
        final var viewCode = accView.getNewCode().orElseThrow();
        final boolean codeMatch =
            balAcc.codeChanges().stream()
                .anyMatch(
                    balChange ->
                        balChange.txIndex() == txIndex && Objects.equals(balChange.newCode(), viewCode));
        if (!codeMatch) {
          return false;
        }
      }

      // TODO: Check storage slot changes
    }

    return true;
  }
}
