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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExpiryValidatorTest {
  private final Clock clock = Clock.fixed(Instant.ofEpochSecond(123), ZoneId.of("UTC"));

  private ExpiryValidator validator;

  @BeforeEach
  public void beforeTest() {
    validator = new ExpiryValidator(clock);
  }

  @Test
  public void testValidateForValidExpiry() {
    Assertions.assertDoesNotThrow(() -> validator.validate(456));
  }

  @Test
  public void testValidateForNegativeExpiry() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(-1));
  }

  @Test
  public void testValidateForExpiredExpiry() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(12));
  }
}
