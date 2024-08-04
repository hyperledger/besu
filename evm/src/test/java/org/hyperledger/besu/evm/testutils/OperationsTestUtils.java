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
package org.hyperledger.besu.evm.testutils;

import static org.hyperledger.besu.evm.internal.Words.readBigEndianI16;
import static org.hyperledger.besu.evm.internal.Words.readBigEndianU16;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.evm.Code;

import org.apache.tuweni.bytes.Bytes;

public class OperationsTestUtils {

  public static Code mockCode(final String codeString) {
    Code mockCode = mock(Code.class);
    final Bytes codeBytes = Bytes.fromHexString(codeString);
    when(mockCode.getBytes()).thenReturn(codeBytes);
    when(mockCode.getEofVersion()).thenReturn(1);
    when(mockCode.readBigEndianI16(anyInt()))
        .thenAnswer(
            invocationOnMock ->
                readBigEndianI16(invocationOnMock.getArgument(0), codeBytes.toArrayUnsafe()));
    when(mockCode.readBigEndianU16(anyInt()))
        .thenAnswer(
            invocationOnMock ->
                readBigEndianU16(invocationOnMock.getArgument(0), codeBytes.toArrayUnsafe()));
    when(mockCode.readU8(anyInt()))
        .thenAnswer(
            invocationOnMock ->
                codeBytes.toArrayUnsafe()[(int) invocationOnMock.getArgument(0)] & 0xff);
    return mockCode;
  }
}
