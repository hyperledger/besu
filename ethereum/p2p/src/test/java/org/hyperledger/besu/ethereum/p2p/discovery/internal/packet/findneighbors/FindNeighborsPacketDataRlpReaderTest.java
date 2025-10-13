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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.findneighbors;

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FindNeighborsPacketDataRlpReaderTest {
  private @Mock FindNeighborsPacketDataFactory factory;

  private FindNeighborsPacketDataRlpReader reader;

  @BeforeEach
  public void beforeTest() {
    reader = new FindNeighborsPacketDataRlpReader(factory);
  }

  @Test
  public void testReadFrom() {
    final RLPInput in = new BytesValueRLPInput(Bytes.fromHexString("0xc684deadbeef7b"), false);
    final Bytes target = Bytes.fromHexString("0xdeadbeef");
    final long expiry = 123;

    Mockito.when(factory.create(target, expiry))
        .thenReturn(new FindNeighborsPacketData(target, expiry));

    FindNeighborsPacketData result = reader.readFrom(in);

    Mockito.verify(factory).create(target, expiry);

    Assertions.assertNotNull(result);
    Assertions.assertEquals(target, result.getTarget());
    Assertions.assertEquals(expiry, result.getExpiration());
  }
}
