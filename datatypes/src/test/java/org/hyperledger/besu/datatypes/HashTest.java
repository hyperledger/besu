/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.datatypes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class HashTest {

  @Test
  public void shouldGetExpectedValueForEmptyHash() {
    assertThat(Hash.EMPTY)
        .isEqualTo(
            Hash.fromHexString("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"));
  }

  @Test
  public void shouldGetExpectedValueForEmptyRequestsHash() {
    assertThat(Hash.EMPTY_REQUESTS_HASH)
        .isEqualTo(
            Hash.fromHexString("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
  }
}
