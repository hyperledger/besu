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

import org.hyperledger.besu.ethereum.core.BlockAccessList;
import org.hyperledger.besu.ethereum.core.BlockAccessList.AccountChanges;
import org.hyperledger.besu.ethereum.core.BlockAccessList.BalanceChange;
import org.hyperledger.besu.ethereum.core.BlockAccessList.CodeChange;
import org.hyperledger.besu.ethereum.core.BlockAccessList.NonceChange;
import org.hyperledger.besu.ethereum.core.BlockAccessList.SlotChanges;
import org.hyperledger.besu.ethereum.core.BlockAccessList.StorageChange;

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
      this.address = changes.getAddress().toString();
      this.storageChanges =
          changes.getStorageChanges().stream().map(SlotChangeParameter::new).toList();
      this.storageReads =
          changes.getStorageReads().stream()
              .map(sr -> sr.getSlot().getSlotKey().map(UInt256::toHexString).orElse(""))
              .toList();
      this.balanceChanges =
          changes.getBalanceChanges().stream().map(BalanceChangeParameter::new).toList();
      // this.nonceChanges =
      //     changes.getNonceChanges().stream().map(NonceChangeParameter::new).toList();
      this.codeChanges = changes.getCodeChanges().stream().map(CodeChangeParameter::new).toList();
    }
  }

  public static class SlotChangeParameter {
    public final String slot;
    public final List<StorageChangeParameter> changes;

    public SlotChangeParameter(final SlotChanges changes) {
      this.slot = changes.getSlot().getSlotKey().map(UInt256::toHexString).orElse("null");
      this.changes = changes.getChanges().stream().map(StorageChangeParameter::new).toList();
    }
  }

  public static class StorageChangeParameter {
    public final int txIndex;
    public final String newValue;

    public StorageChangeParameter(final StorageChange change) {
      this.txIndex = change.getTxIndex();
      this.newValue = change.getNewValue().toHexString();
    }
  }

  public static class BalanceChangeParameter {
    public final int txIndex;
    public final String postBalance;

    public BalanceChangeParameter(final BalanceChange change) {
      this.txIndex = change.getTxIndex();
      this.postBalance = change.getPostBalance().toHexString();
    }
  }

  public static class NonceChangeParameter {
    public final int txIndex;
    public final long newNonce;

    public NonceChangeParameter(final NonceChange change) {
      this.txIndex = change.getTxIndex();
      this.newNonce = change.getNewNonce();
    }
  }

  public static class CodeChangeParameter {
    public final int txIndex;
    public final String newCode;

    public CodeChangeParameter(final CodeChange change) {
      this.txIndex = change.getTxIndex();
      this.newCode = change.getNewCode().toBase64String();
    }
  }
}
