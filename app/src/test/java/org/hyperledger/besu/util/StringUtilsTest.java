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
package org.hyperledger.besu.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class StringUtilsTest {

  @Test
  public void joiningWithLastDelimiter() {
    Map<List<String>, String> testCases = new HashMap<>();
    //noinspection ArraysAsListWithZeroOrOneArgument
    testCases.put(Arrays.asList("item1"), "item1");
    testCases.put(Arrays.asList("item1", "item2"), "item1 and item2");
    testCases.put(Arrays.asList("item1", "item2", "item3"), "item1, item2 and item3");

    for (Map.Entry<List<String>, String> entry : testCases.entrySet()) {
      String joinedResult =
          entry.getKey().stream()
              .collect(
                  Collectors.collectingAndThen(
                      Collectors.toList(), StringUtils.joiningWithLastDelimiter(", ", " and ")));
      assertThat(joinedResult).isEqualTo(entry.getValue());
    }
  }
}
