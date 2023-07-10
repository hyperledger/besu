/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.evm.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.SelfDestructOperation;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

import java.util.ArrayDeque;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SelfDestructOperationTest {

  private static final Bytes SELFDESTRUCT_CODE =
      Bytes.fromHexString(
          "6000" // PUSH1 0
              + "35" // CALLDATALOAD
              + "ff" // SELFDESTRUCT
          );

  private MessageFrame messageFrame;
  @Mock private WorldUpdater worldUpdater;
  @Mock private WrappedEvmAccount accountContract;
  @Mock private WrappedEvmAccount accountBeneficiary;
  @Mock private MutableAccount mutableAccountContract;
  @Mock private MutableAccount mutableAccountBeneficiary;
  @Mock private EVM evm;

  private final SelfDestructOperation frontierOperation =
      new SelfDestructOperation(new ConstantinopleGasCalculator());

  private final SelfDestructOperation eip6780Operation =
      new SelfDestructOperation(new ConstantinopleGasCalculator(), true);

  void checkContractDeletionCommon(
      final String contract,
      final String beneficiary,
      final String balanceHex,
      final boolean newContract,
      final SelfDestructOperation operation) {
    Address contractAddress = Address.fromHexString(contract);
    Address beneficiaryAddress = Address.fromHexString(beneficiary);
    messageFrame =
        MessageFrame.builder()
            .type(MessageFrame.Type.CONTRACT_CREATION)
            .contract(Address.ZERO)
            .inputData(Bytes.EMPTY)
            .sender(beneficiaryAddress)
            .value(Wei.ZERO)
            .apparentValue(Wei.ZERO)
            .code(CodeFactory.createCode(SELFDESTRUCT_CODE, 0, true))
            .depth(1)
            .completer(__ -> {})
            .address(contractAddress)
            .blockHashLookup(n -> Hash.hash(Words.longBytes(n)))
            .blockValues(mock(BlockValues.class))
            .gasPrice(Wei.ZERO)
            .messageFrameStack(new ArrayDeque<>())
            .miningBeneficiary(Address.ZERO)
            .originator(Address.ZERO)
            .initialGas(100_000L)
            .worldUpdater(worldUpdater)
            .build();
    messageFrame.pushStackItem(Bytes.fromHexString(beneficiary));
    if (newContract) {
      messageFrame.addCreate(contractAddress);
    }

    when(accountContract.getMutable()).thenReturn(mutableAccountContract);
    if (!contract.equals(beneficiary)) {
      when(mutableAccountContract.getBalance()).thenReturn(Wei.fromHexString(balanceHex));
    }
    when(accountBeneficiary.getMutable()).thenReturn(mutableAccountBeneficiary);
    when(mutableAccountBeneficiary.getAddress()).thenReturn(beneficiaryAddress);
    when(worldUpdater.getAccount(contractAddress)).thenReturn(accountContract);
    when(worldUpdater.get(contractAddress)).thenReturn(accountContract);
    when(worldUpdater.get(beneficiaryAddress)).thenReturn(accountBeneficiary);
    when(worldUpdater.getOrCreate(beneficiaryAddress)).thenReturn(accountBeneficiary);

    final Operation.OperationResult operationResult = operation.execute(messageFrame, evm);
    assertThat(operationResult).isNotNull();

    // The interactions with the contracts varies based on the parameterized tests, but it will be
    // some subset of these calls.
    verify(accountContract, atLeast(0)).getBalance();
    verify(accountBeneficiary, atLeast(0)).getBalance();
    verify(accountBeneficiary).isEmpty();
    verify(mutableAccountContract, atLeast(0)).getBalance();
    verify(mutableAccountContract, atLeast(0)).setBalance(Wei.ZERO);
    if (!contract.equals(beneficiary)) {
      verify(mutableAccountBeneficiary).incrementBalance(Wei.fromHexString(balanceHex));
    }
  }

  public static Object[][] params() {
    return new Object[][] {
      {
        "0x00112233445566778899aabbccddeeff11223344",
        "0x1234567890abcdef1234567890abcdef12345678",
        true,
        "0x1234567890"
      },
      {
        "0x00112233445566778899aabbccddeeff11223344",
        "0x1234567890abcdef1234567890abcdef12345678",
        false,
        "0x1234567890"
      },
      {
        "0x00112233445566778899aabbccddeeff11223344",
        "0x00112233445566778899aabbccddeeff11223344",
        true,
        "0x1234567890"
      },
      {
        "0x1234567890abcdef1234567890abcdef12345678",
        "0x1234567890abcdef1234567890abcdef12345678",
        false,
        "0x1234567890"
      },
    };
  }

  @ParameterizedTest
  @MethodSource("params")
  void checkContractDeletionFrontier(
      final String contract,
      final String beneficiary,
      final boolean newAccount,
      final String balanceHex) {
    when(mutableAccountContract.getAddress()).thenReturn(Address.fromHexString(contract));
    checkContractDeletionCommon(contract, beneficiary, balanceHex, newAccount, frontierOperation);

    assertThat(messageFrame.getSelfDestructs()).contains(Address.fromHexString(contract));
  }

  @ParameterizedTest
  @MethodSource("params")
  void checkContractDeletionEIP6780(
      final String contract,
      final String beneficiary,
      final boolean newAccount,
      final String balanceHex) {
    checkContractDeletionCommon(contract, beneficiary, balanceHex, newAccount, eip6780Operation);

    Address contractAddress = Address.fromHexString(contract);
    if (newAccount) {
      assertThat(messageFrame.getSelfDestructs()).contains(contractAddress);
      assertThat(messageFrame.getCreates()).contains(contractAddress);
    } else {
      assertThat(messageFrame.getSelfDestructs()).isEmpty();
      assertThat(messageFrame.getCreates()).doesNotContain(contractAddress);
    }
  }
}
