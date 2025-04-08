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
package org.hyperledger.besu.evm.worldstate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.CodeDelegationAccount;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CodeDelegationHelperTest {

  private static final Bytes CODE_PREFIX = CodeDelegationHelper.CODE_DELEGATION_PREFIX;
  private static final int DELEGATED_SIZE = CodeDelegationHelper.DELEGATED_CODE_SIZE;

  private @Mock WorldUpdater worldUpdater;
  private @Mock GasCalculator gasCalculator;
  private @Mock CodeDelegationAccount account;

  @Test
  void hasCodeDelegationReturnsFalseForNull() {
    assertThat(CodeDelegationHelper.hasCodeDelegation(null)).isFalse();
  }

  @Test
  void hasCodeDelegationReturnsFalseForWrongSize() {
    Bytes wrongSizeCode = Bytes.fromHexString("ef010001020304"); // arbitrary extra bytes
    assertThat(CodeDelegationHelper.hasCodeDelegation(wrongSizeCode)).isFalse();
  }

  @Test
  void hasCodeDelegationReturnsFalseForWrongPrefix() {
    Bytes wrongPrefix = Bytes.concatenate(Bytes.fromHexString("abcd00"), Bytes.random(20));
    assertThat(CodeDelegationHelper.hasCodeDelegation(wrongPrefix)).isFalse();
  }

  @Test
  void hasCodeDelegationReturnsTrueForValidDelegationCode() {
    Bytes validCode = Bytes.concatenate(CODE_PREFIX, Bytes.random(20));
    assertThat(validCode.size()).isEqualTo(DELEGATED_SIZE);
    assertThat(CodeDelegationHelper.hasCodeDelegation(validCode)).isTrue();
  }

  @Test
  void getTargetAccountReturnsEmptyIfAccountIsNull() {
    assertThatThrownBy(
            () -> CodeDelegationHelper.getTargetAccount(worldUpdater, gasCalculator, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Account must not be null.");
  }

  @Test
  void getTargetAccountReturnsEmptyIfNoDelegation() {
    Bytes code = Bytes.fromHexString("600035"); // random code, not delegated
    when(account.getCode()).thenReturn(code);

    assertThatThrownBy(
            () -> CodeDelegationHelper.getTargetAccount(worldUpdater, gasCalculator, account))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Account does not have code delegation.");
  }

  @Test
  void getTargetAccountReturnsEmptyIfTargetAccountIsNull() {
    Bytes validCode = Bytes.concatenate(CODE_PREFIX, Bytes.random(20));
    when(account.getCode()).thenReturn(validCode);

    Address targetAddress = Address.wrap(validCode.slice(CODE_PREFIX.size()));
    when(worldUpdater.get(targetAddress)).thenReturn(null);

    CodeDelegationAccount result =
        CodeDelegationHelper.getTargetAccount(worldUpdater, gasCalculator, account);

    assertThat(result.getCode()).isEqualTo(Bytes.EMPTY);
  }

  @Test
  void getTargetAccountReturnsEmptyCodeIfTargetIsPrecompile() {
    Bytes validCode = Bytes.concatenate(CODE_PREFIX, Bytes.random(20));
    when(account.getCode()).thenReturn(validCode);

    Address targetAddress = Address.wrap(validCode.slice(CODE_PREFIX.size()));
    Account targetAccount = mock(Account.class);

    when(worldUpdater.get(targetAddress)).thenReturn(targetAccount);
    when(gasCalculator.isPrecompile(targetAddress)).thenReturn(true);

    CodeDelegationAccount result =
        CodeDelegationHelper.getTargetAccount(worldUpdater, gasCalculator, account);

    assertThat(result.getCode()).isEqualTo(Bytes.EMPTY);
  }

  @Test
  void getTargetAccountReturnsTargetCodeIfValid() {
    Bytes validCode = Bytes.concatenate(CODE_PREFIX, Bytes.random(20));
    when(account.getCode()).thenReturn(validCode);

    Address targetAddress = Address.wrap(validCode.slice(CODE_PREFIX.size()));
    Account targetAccount = mock(Account.class);
    Bytes targetCode = Bytes.fromHexString("60006000");

    when(worldUpdater.get(targetAddress)).thenReturn(targetAccount);
    when(targetAccount.getCode()).thenReturn(targetCode);
    when(gasCalculator.isPrecompile(targetAddress)).thenReturn(false);

    CodeDelegationAccount result =
        CodeDelegationHelper.getTargetAccount(worldUpdater, gasCalculator, account);

    assertThat(result.getTargetAddress()).isEqualTo(targetAddress);
    assertThat(result.getCode()).isEqualTo(targetCode);
  }
}
