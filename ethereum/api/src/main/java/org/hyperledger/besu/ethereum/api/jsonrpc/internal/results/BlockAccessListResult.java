/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.AccountChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.BalanceChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.CodeChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.NonceChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.SlotChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.StorageChange;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.units.bigints.UInt256;

public class BlockAccessListResult {

  private final List<AccountChangesResult> accountChanges;

  @JsonCreator
  public BlockAccessListResult(
      @JsonProperty("accountChanges") final List<AccountChangesResult> accountChanges) {
    this.accountChanges = accountChanges;
  }

  public static BlockAccessListResult fromBlockAccessList(final BlockAccessList list) {
    return new BlockAccessListResult(
        list.getAccountChanges().stream().map(AccountChangesResult::new).toList());
  }

  public List<AccountChangesResult> getAccountChanges() {
    return accountChanges;
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public static class AccountChangesResult {
    public final String address;
    public final List<SlotChangeResult> storageChanges;
    public final List<String> storageReads;
    public final List<BalanceChangeResult> balanceChanges;
    public final List<NonceChangeResult> nonceChanges;
    public final List<CodeChangeResult> codeChanges;

    public AccountChangesResult(final AccountChanges changes) {
      this.address = changes.address().toString();
      this.storageChanges = changes.storageChanges().stream().map(SlotChangeResult::new).toList();
      this.storageReads =
          changes.storageReads().stream()
              .map(sr -> sr.slot().getSlotKey().map(UInt256::toHexString).orElse(""))
              .toList();
      this.balanceChanges =
          changes.balanceChanges().stream().map(BalanceChangeResult::new).toList();
      this.nonceChanges = changes.nonceChanges().stream().map(NonceChangeResult::new).toList();
      this.codeChanges = changes.codeChanges().stream().map(CodeChangeResult::new).toList();
    }
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public static class SlotChangeResult {
    public final String slot;
    public final List<StorageChangeResult> changes;

    public SlotChangeResult(final SlotChanges changes) {
      this.slot = changes.slot().getSlotKey().map(UInt256::toHexString).orElse("null");
      this.changes = changes.changes().stream().map(StorageChangeResult::new).toList();
    }
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public static class StorageChangeResult {
    public final long txIndex;
    public final String newValue;

    public StorageChangeResult(final StorageChange change) {
      this.txIndex = change.txIndex();
      this.newValue = change.newValue().toHexString();
    }
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public static class BalanceChangeResult {
    public final long txIndex;
    public final String postBalance;

    public BalanceChangeResult(final BalanceChange change) {
      this.txIndex = change.txIndex();
      this.postBalance = change.postBalance().toHexString();
    }
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public static class NonceChangeResult {
    public final long txIndex;
    public final long newNonce;

    public NonceChangeResult(final NonceChange change) {
      this.txIndex = change.txIndex();
      this.newNonce = change.newNonce();
    }
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public static class CodeChangeResult {
    public final long txIndex;
    public final String newCode;

    public CodeChangeResult(final CodeChange change) {
      this.txIndex = change.txIndex();
      this.newCode = change.newCode().toBase64String();
    }
  }
}
