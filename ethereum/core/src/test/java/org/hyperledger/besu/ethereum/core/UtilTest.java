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
package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.api.Java6Assertions.assertThat;

import org.junit.Test;

public class UtilTest {
  @Test
  public void testFastDivCeil() {
    assertThat(Util.fastDivCeiling(0, 3)).isEqualTo(1);
    assertThat(Util.fastDivCeiling(1, 3)).isEqualTo(1);
    assertThat(Util.fastDivCeiling(2, 3)).isEqualTo(1);
    assertThat(Util.fastDivCeiling(3, 3)).isEqualTo(1);

    assertThat(Util.fastDivCeiling(4, 3)).isEqualTo(2);
    assertThat(Util.fastDivCeiling(5, 3)).isEqualTo(2);
    assertThat(Util.fastDivCeiling(6, 3)).isEqualTo(2);

    assertThat(Util.fastDivCeiling(7, 3)).isEqualTo(3);
  }
}
