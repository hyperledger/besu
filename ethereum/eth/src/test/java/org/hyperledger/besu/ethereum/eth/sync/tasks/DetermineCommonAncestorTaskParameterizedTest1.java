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
package org.hyperledger.besu.ethereum.eth.sync.tasks;

import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;

public class DetermineCommonAncestorTaskParameterizedTest1
    extends AbstractDetermineCommonAncestorTaskParameterizedTest {

  public static Stream<Arguments> parameters() {
    final int[] requestSizes = {5, 12, chainHeight, chainHeight * 2};
    final Stream.Builder<Arguments> builder = Stream.builder();
    for (final int requestSize : requestSizes) {
      for (int i = 0; i <= chainHeight; i++) {
        builder.add(Arguments.of(requestSize, i, true));
      }
    }
    return builder.build();
  }
}
