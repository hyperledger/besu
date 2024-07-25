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
package org.hyperledger.besu.evm.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class BlobHashOperationTest {

  private static final String testVersionedHash =
      "0x01cafebabeb0b0facedeadbeefbeef0001cafebabeb0b0facedeadbeefbeef00";

  @Test
  void putsHashOnStack() {
    VersionedHash version0Hash = new VersionedHash(Bytes32.fromHexStringStrict(testVersionedHash));
    List<VersionedHash> versionedHashes = Arrays.asList(version0Hash);
    BlobHashOperation getHash = new BlobHashOperation(new LondonGasCalculator());
    MessageFrame frame = mock(MessageFrame.class);
    when(frame.popStackItem()).thenReturn(Bytes.of(0));
    when(frame.getVersionedHashes()).thenReturn(Optional.of(versionedHashes));
    EVM fakeEVM = mock(EVM.class);
    Operation.OperationResult r = getHash.execute(frame, fakeEVM);
    assertThat(r.getGasCost()).isEqualTo(3);
    assertThat(r.getHaltReason()).isNull();
    verify(frame).pushStackItem(version0Hash.toBytes());
  }

  @Test
  void pushesZeroOnBloblessTx() {

    EVM fakeEVM = mock(EVM.class);

    BlobHashOperation getHash = new BlobHashOperation(new CancunGasCalculator());
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
  void pushZeroOnVersionIndexOutOFBounds() {
    VersionedHash version0Hash = new VersionedHash(Bytes32.fromHexStringStrict(testVersionedHash));
    List<VersionedHash> versionedHashes = Arrays.asList(version0Hash);
    BlobHashOperation getHash = new BlobHashOperation(new CancunGasCalculator());
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
    VersionedHash version0Hash = new VersionedHash(Bytes32.fromHexStringStrict(testVersionedHash));
    List<VersionedHash> versionedHashes = Arrays.asList(version0Hash);
    BlobHashOperation getHash = new BlobHashOperation(new CancunGasCalculator());
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
