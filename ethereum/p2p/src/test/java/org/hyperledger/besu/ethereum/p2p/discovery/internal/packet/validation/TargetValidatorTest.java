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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.validation;

import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.findneighbors.FindNeighborsPacketData;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TargetValidatorTest {
  private TargetValidator validator;

  @BeforeEach
  public void beforeTest() {
    validator = new TargetValidator();
  }

  @Test
  public void testValidateWithValidTarget() {
    Assertions.assertDoesNotThrow(
        () -> validator.validate(Bytes.repeat((byte) 0x00, FindNeighborsPacketData.TARGET_SIZE)));
  }

  @Test
  public void testValidateWithNullTarget() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(null));
  }

  @Test
  public void testValidateWithInvalidTargetSize() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            validator.validate(Bytes.repeat((byte) 0x00, FindNeighborsPacketData.TARGET_SIZE + 1)));
  }
}
