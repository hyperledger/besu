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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.enrresponse;

import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.validation.NodeRecordValidator;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.validation.RequestHashValidator;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EnrResponsePacketDataFactoryTest {
  private @Mock RequestHashValidator requestHashValidator;
  private @Mock NodeRecordValidator nodeRecordValidator;

  private EnrResponsePacketDataFactory factory;

  @BeforeEach
  public void beforeTest() {
    factory = new EnrResponsePacketDataFactory(requestHashValidator, nodeRecordValidator);
  }

  @Test
  public void testCreate() {
    final Bytes requestHash = Mockito.mock(Bytes.class);
    final NodeRecord enr = Mockito.mock(NodeRecord.class);

    EnrResponsePacketData result = factory.create(requestHash, enr);

    Mockito.verify(requestHashValidator).validate(requestHash);
    Mockito.verify(nodeRecordValidator).validate(enr);

    Assertions.assertEquals(requestHash, result.getRequestHash());
    Assertions.assertEquals(enr, result.getEnr());
  }

  @Test
  public void testCreateWithInvalidRequestHash() {
    final Bytes requestHash = Mockito.mock(Bytes.class);
    final NodeRecord enr = Mockito.mock(NodeRecord.class);

    Mockito.doThrow(new IllegalArgumentException())
        .when(requestHashValidator)
        .validate(requestHash);

    Assertions.assertThrows(IllegalArgumentException.class, () -> factory.create(requestHash, enr));

    Mockito.verify(requestHashValidator).validate(requestHash);
    Mockito.verifyNoInteractions(nodeRecordValidator);
  }

  @Test
  public void testCreateWithInvalidNodeRecord() {
    final Bytes requestHash = Mockito.mock(Bytes.class);
    final NodeRecord enr = Mockito.mock(NodeRecord.class);

    Mockito.doThrow(new IllegalArgumentException()).when(nodeRecordValidator).validate(enr);

    Assertions.assertThrows(IllegalArgumentException.class, () -> factory.create(requestHash, enr));

    Mockito.verify(requestHashValidator).validate(requestHash);
    Mockito.verify(nodeRecordValidator).validate(enr);
  }
}
