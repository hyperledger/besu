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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.AccountChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.BalanceChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.CodeChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.NonceChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.SlotChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.StorageChange;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.units.bigints.UInt256;

public class BlockAccessListParameter {

  private final List<AccountChangesParameter> accountChanges;

  @JsonCreator
  public BlockAccessListParameter(
      @JsonProperty("accountChanges") final List<AccountChangesParameter> accountChanges) {
    this.accountChanges = accountChanges;
  }

  public static BlockAccessListParameter fromBlockAccessList(final BlockAccessList list) {
    return new BlockAccessListParameter(
        list.getAccountChanges().stream().map(AccountChangesParameter::new).toList());
  }

  public List<AccountChangesParameter> getAccountChanges() {
    return accountChanges;
  }

  public static class AccountChangesParameter {
    public final String address;
    public final List<SlotChangeParameter> storageChanges;
    public final List<String> storageReads;
    public final List<BalanceChangeParameter> balanceChanges;
    // public final List<NonceChangeParameter> nonceChanges;
    public final List<CodeChangeParameter> codeChanges;

    public AccountChangesParameter(final AccountChanges changes) {
      this.address = changes.address().toString();
      this.storageChanges =
          changes.storageChanges().stream().map(SlotChangeParameter::new).toList();
      this.storageReads =
          changes.storageReads().stream()
              .map(sr -> sr.slot().getSlotKey().map(UInt256::toHexString).orElse(""))
              .toList();
      this.balanceChanges =
          changes.balanceChanges().stream().map(BalanceChangeParameter::new).toList();
      // this.nonceChanges =
      //     changes.getNonceChanges().stream().map(NonceChangeParameter::new).toList();
      this.codeChanges = changes.codeChanges().stream().map(CodeChangeParameter::new).toList();
    }
  }

  public static class SlotChangeParameter {
    public final String slot;
    public final List<StorageChangeParameter> changes;

    public SlotChangeParameter(final SlotChanges changes) {
      this.slot = changes.slot().getSlotKey().map(UInt256::toHexString).orElse("null");
      this.changes = changes.changes().stream().map(StorageChangeParameter::new).toList();
    }
  }

  public static class StorageChangeParameter {
    public final long txIndex;
    public final String newValue;

    public StorageChangeParameter(final StorageChange change) {
      this.txIndex = change.txIndex();
      this.newValue = change.newValue().toHexString();
    }
  }

  public static class BalanceChangeParameter {
    public final long txIndex;
    public final String postBalance;

    public BalanceChangeParameter(final BalanceChange change) {
      this.txIndex = change.txIndex();
      this.postBalance = change.postBalance().toHexString();
    }
  }

  public static class NonceChangeParameter {
    public final long txIndex;
    public final long newNonce;

    public NonceChangeParameter(final NonceChange change) {
      this.txIndex = change.txIndex();
      this.newNonce = change.newNonce();
    }
  }

  public static class CodeChangeParameter {
    public final long txIndex;
    public final String newCode;

    public CodeChangeParameter(final CodeChange change) {
      this.txIndex = change.txIndex();
      this.newCode = change.newCode().toBase64String();
    }
  }
}
