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

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class RequestHashValidatorTest {
  private RequestHashValidator validator;

  @BeforeEach
  public void beforeTest() {
    validator = new RequestHashValidator();
  }

  @Test
  public void testValidateForValidHash() {
    Assertions.assertDoesNotThrow(() -> validator.validate(Mockito.mock(Bytes.class)));
  }

  @Test
  public void testValidateForInvalidHash() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(null));
  }
}
