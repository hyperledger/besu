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

import org.hyperledger.besu.datatypes.RequestType;
import org.hyperledger.besu.ethereum.core.Request;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ProhibitedRequestValidatorTest {

  @Test
  void validateTrueWhenNoRequests() {
    ProhibitedRequestValidator validator = new ProhibitedRequestValidator();
    Assertions.assertThat(validator.validate(Optional.empty())).isTrue();
  }

  @Test
  void validateFalseWhenHasEmptyListOfRequests() {
    ProhibitedRequestValidator validator = new ProhibitedRequestValidator();
    Assertions.assertThat(validator.validate(Optional.of(List.of()))).isFalse();
  }

  @Test
  void validateFalseWhenHasRequests() {
    ProhibitedRequestValidator validator = new ProhibitedRequestValidator();
    List<Request> requests = List.of(new Request(RequestType.DEPOSIT, Bytes.of(1)));
    Assertions.assertThat(validator.validate(Optional.of(requests))).isFalse();
  }
}
