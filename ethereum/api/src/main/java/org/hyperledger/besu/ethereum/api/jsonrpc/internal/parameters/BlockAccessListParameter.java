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
import org.hyperledger.besu.ethereum.core.BlockAccessList.AccountAccess;
import org.hyperledger.besu.ethereum.core.BlockAccessList.AccountBalanceDiff;
import org.hyperledger.besu.ethereum.core.BlockAccessList.AccountCodeDiff;
import org.hyperledger.besu.ethereum.core.BlockAccessList.AccountNonceDiff;
import org.hyperledger.besu.ethereum.core.BlockAccessList.BalanceChange;
import org.hyperledger.besu.ethereum.core.BlockAccessList.CodeChange;
import org.hyperledger.besu.ethereum.core.BlockAccessList.PerTxAccess;
import org.hyperledger.besu.ethereum.core.BlockAccessList.SlotAccess;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class BlockAccessListParameter {

  private final List<AccountAccessParameter> accountAccesses;
  private final List<AccountBalanceDiffParameter> balanceDiffs;
  private final List<AccountCodeDiffParameter> codeDiffs;
  private final List<AccountNonceDiffParameter> nonceDiffs;

  @JsonCreator
  public BlockAccessListParameter(
      @JsonProperty("accountAccesses") final List<AccountAccessParameter> accountAccesses,
      @JsonProperty("balanceDiffs") final List<AccountBalanceDiffParameter> balanceDiffs,
      @JsonProperty("codeDiffs") final List<AccountCodeDiffParameter> codeDiffs,
      @JsonProperty("nonceDiffs") final List<AccountNonceDiffParameter> nonceDiffs) {
    this.accountAccesses = accountAccesses;
    this.balanceDiffs = balanceDiffs;
    this.codeDiffs = codeDiffs;
    this.nonceDiffs = nonceDiffs;
  }

  public static BlockAccessListParameter fromBlockAccessList(final BlockAccessList list) {
    return new BlockAccessListParameter(
        list.getAccountAccesses().stream().map(AccountAccessParameter::new).toList(),
        list.getAccountBalanceDiffs().stream().map(AccountBalanceDiffParameter::new).toList(),
        list.getAccountCodeDiffs().stream().map(AccountCodeDiffParameter::new).toList(),
        list.getAccountNonceDiffs().stream().map(AccountNonceDiffParameter::new).toList());
  }

  public List<AccountAccessParameter> getAccountAccesses() {
    return accountAccesses;
  }

  public List<AccountBalanceDiffParameter> getBalanceDiffs() {
    return balanceDiffs;
  }

  public List<AccountCodeDiffParameter> getCodeDiffs() {
    return codeDiffs;
  }

  public List<AccountNonceDiffParameter> getNonceDiffs() {
    return nonceDiffs;
  }

  public static class AccountAccessParameter {
    public final String address;
    public final List<SlotAccessParameter> slotAccesses;

    public AccountAccessParameter(final AccountAccess access) {
      this.address = access.getAddress().toString();
      this.slotAccesses = access.getSlotAccesses().stream().map(SlotAccessParameter::new).toList();
    }
  }

  public static class SlotAccessParameter {
    public final String slot;
    public final List<PerTxAccessParameter> accesses;

    public SlotAccessParameter(final SlotAccess access) {
      this.slot = access.getSlot().getSlotKey().map(UInt256::toHexString).orElse("null");
      this.accesses = access.getPerTxAccesses().stream().map(PerTxAccessParameter::new).toList();
    }
  }

  public static class PerTxAccessParameter {
    public final int txIndex;
    public final String valueAfter;

    public PerTxAccessParameter(final PerTxAccess access) {
      this.txIndex = access.getTxIndex();
      this.valueAfter = access.getValueAfter().map(Bytes::toHexString).orElse(null);
    }
  }

  public static class AccountBalanceDiffParameter {
    public final String address;
    public final List<BalanceChangeParameter> changes;

    public AccountBalanceDiffParameter(final AccountBalanceDiff diff) {
      this.address = diff.getAddress().toString();
      this.changes = diff.getBalanceChanges().stream().map(BalanceChangeParameter::new).toList();
    }
  }

  public static class BalanceChangeParameter {
    public final int txIndex;
    public final String delta;

    public BalanceChangeParameter(final BalanceChange change) {
      this.txIndex = change.getTxIndex();
      this.delta = change.getDelta().toString();
    }
  }

  public static class AccountCodeDiffParameter {
    public final String address;
    public final CodeChangeParameter change;

    public AccountCodeDiffParameter(final AccountCodeDiff diff) {
      this.address = diff.getAddress().toString();
      this.change = new CodeChangeParameter(diff.getChange());
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

  public static class AccountNonceDiffParameter {
    public final String address;
    public final long nonceBefore;

    public AccountNonceDiffParameter(final AccountNonceDiff diff) {
      this.address = diff.getAddress().toString();
      this.nonceBefore = diff.getNonceBefore();
    }
  }
}
