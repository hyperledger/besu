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
package org.hyperledger.besu.ethereum.mainnet.requests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hyperledger.besu.datatypes.RequestType;
import org.hyperledger.besu.ethereum.core.Request;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class MainnetRequestsValidatorTest {

  @Test
  void validateFalseWhenNoRequests() {
    MainnetRequestsValidator validator = new MainnetRequestsValidator();
    assertFalse(validator.validate(Optional.empty()));
  }

  @Test
  void validateFalseWhenRequestsNotInOrder() {
    MainnetRequestsValidator validator = new MainnetRequestsValidator();
    List<Request> requests =
        List.of(
            new Request(RequestType.WITHDRAWAL, Bytes.of(3)),
            new Request(RequestType.DEPOSIT, Bytes.of(1)),
            new Request(RequestType.CONSOLIDATION, Bytes.of(2)));
    assertFalse(validator.validate(Optional.of(requests)));
  }

  @Test
  void validateTrueForValidRequests() {
    MainnetRequestsValidator validator = new MainnetRequestsValidator();
    List<Request> requests =
        List.of(
            new Request(RequestType.DEPOSIT, Bytes.of(1)),
            new Request(RequestType.WITHDRAWAL, Bytes.of(2)),
            new Request(RequestType.CONSOLIDATION, Bytes.of(3)));
    assertTrue(validator.validate(Optional.of(requests)));
  }
}
