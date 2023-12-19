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
package org.hyperledger.besu.consensus.common.bft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class SizeLimitedMapTest {

  @Test
  public void evictMessageRecordAtCapacity() {
    SizeLimitedMap<String, Boolean> map = new SizeLimitedMap<>(5);

    map.put("message1", true);
    assertThat(map).hasSize(1);

    // add messages so map is at capacity
    for (int i = 2; i <= 5; i++) {
      map.put("message" + i, true);
    }
    assertThat(map).hasSize(5);

    map.put("message6", false);
    assertThat(map).hasSize(5);
    assertThat(map.keySet()).doesNotContain("message1");
    assertThat(map.keySet()).contains("message2", "message3", "message4", "message5", "message6");

    map.put("message7", true);
    assertThat(map).hasSize(5);
    assertThat(map.keySet()).doesNotContain("message1", "message2");
    assertThat(map.keySet()).contains("message3", "message4", "message5", "message6", "message7");
  }
}
