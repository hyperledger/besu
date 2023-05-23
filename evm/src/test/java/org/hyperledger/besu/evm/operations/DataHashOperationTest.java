/*
 * Copyright Hyperledger Besu Contributors.
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ShanghaiGasCalculator;
import org.hyperledger.besu.evm.operation.DataHashOperation;
import org.hyperledger.besu.evm.operation.Operation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Test;

public class DataHashOperationTest {

  @Test
  public void putsHashOnStack() {
    Hash version0Hash = Hash.fromHexStringLenient("0xcafebabeb0b0facedeadbeef");
    List<Hash> versionedHashes = Arrays.asList(version0Hash);
    DataHashOperation getHash = new DataHashOperation(new LondonGasCalculator());
    MessageFrame frame = mock(MessageFrame.class);
    when(frame.popStackItem()).thenReturn(Bytes.of(0));
    when(frame.getVersionedHashes()).thenReturn(Optional.of(versionedHashes));
    EVM fakeEVM = mock(EVM.class);
    Operation.OperationResult r = getHash.execute(frame, fakeEVM);
    assertThat(r.getGasCost()).isEqualTo(3);
    assertThat(r.getHaltReason()).isEqualTo(null);
    verify(frame).pushStackItem(version0Hash);
  }

  @Test
  public void pushesZeroOnBloblessTx() {

    EVM fakeEVM = mock(EVM.class);

    DataHashOperation getHash = new DataHashOperation(new ShanghaiGasCalculator());
    MessageFrame frame = mock(MessageFrame.class);
    when(frame.popStackItem()).thenReturn(Bytes.of(0));
    when(frame.getVersionedHashes()).thenReturn(Optional.empty());

    Operation.OperationResult failed1 = getHash.execute(frame, fakeEVM);
    assertThat(failed1.getGasCost()).isEqualTo(3);
    assertThat(failed1.getHaltReason()).isNull();

    when(frame.popStackItem()).thenReturn(Bytes.of(0));
    when(frame.getVersionedHashes()).thenReturn(Optional.of(new ArrayList<>()));
    Operation.OperationResult failed2 = getHash.execute(frame, fakeEVM);
    assertThat(failed2.getGasCost()).isEqualTo(3);
    assertThat(failed2.getHaltReason()).isNull();
    verify(frame, times(2)).pushStackItem(Bytes.EMPTY);
  }

  @Test
  public void pushZeroOnVersionIndexOutOFBounds() {
    Hash version0Hash = Hash.fromHexStringLenient("0xcafebabeb0b0facedeadbeef");
    List<Hash> versionedHashes = Arrays.asList(version0Hash);
    DataHashOperation getHash = new DataHashOperation(new ShanghaiGasCalculator());
    MessageFrame frame = mock(MessageFrame.class);
    when(frame.popStackItem()).thenReturn(Bytes.of(1));
    when(frame.getVersionedHashes()).thenReturn(Optional.of(versionedHashes));
    EVM fakeEVM = mock(EVM.class);
    Operation.OperationResult r = getHash.execute(frame, fakeEVM);
    assertThat(r.getGasCost()).isEqualTo(3);
    assertThat(r.getHaltReason()).isNull();
    verify(frame).pushStackItem(Bytes.EMPTY);
  }

  @Test
  public void pushZeroWhenPopsMissingUint256SizedIndex() {
    Hash version0Hash = Hash.fromHexStringLenient("0xcafebabeb0b0facedeadbeef");
    List<Hash> versionedHashes = Arrays.asList(version0Hash);
    DataHashOperation getHash = new DataHashOperation(new ShanghaiGasCalculator());
    MessageFrame frame = mock(MessageFrame.class);
    when(frame.popStackItem()).thenReturn(Bytes32.repeat((byte) 0x2C));
    when(frame.getVersionedHashes()).thenReturn(Optional.of(versionedHashes));
    EVM fakeEVM = mock(EVM.class);
    Operation.OperationResult r = getHash.execute(frame, fakeEVM);
    assertThat(r.getGasCost()).isEqualTo(3);
    assertThat(r.getHaltReason()).isNull();
    verify(frame).pushStackItem(Bytes.EMPTY);
  }
}
