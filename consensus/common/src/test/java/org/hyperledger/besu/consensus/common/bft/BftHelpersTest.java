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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class BftHelpersTest {

  @Test
  public void calculateRequiredValidatorQuorum1Validator() {
    Assertions.assertThat(BftHelpers.calculateRequiredValidatorQuorum(1)).isEqualTo(1);
  }

  @Test
  public void calculateRequiredValidatorQuorum2Validator() {
    Assertions.assertThat(BftHelpers.calculateRequiredValidatorQuorum(2)).isEqualTo(2);
  }

  @Test
  public void calculateRequiredValidatorQuorum3Validator() {
    Assertions.assertThat(BftHelpers.calculateRequiredValidatorQuorum(3)).isEqualTo(2);
  }

  @Test
  public void calculateRequiredValidatorQuorum4Validator() {
    Assertions.assertThat(BftHelpers.calculateRequiredValidatorQuorum(4)).isEqualTo(3);
  }

  @Test
  public void calculateRequiredValidatorQuorum5Validator() {
    Assertions.assertThat(BftHelpers.calculateRequiredValidatorQuorum(5)).isEqualTo(4);
  }

  @Test
  public void calculateRequiredValidatorQuorum7Validator() {
    Assertions.assertThat(BftHelpers.calculateRequiredValidatorQuorum(7)).isEqualTo(5);
  }

  @Test
  public void calculateRequiredValidatorQuorum10Validator() {
    Assertions.assertThat(BftHelpers.calculateRequiredValidatorQuorum(10)).isEqualTo(7);
  }

  @Test
  public void calculateRequiredValidatorQuorum15Validator() {
    Assertions.assertThat(BftHelpers.calculateRequiredValidatorQuorum(15)).isEqualTo(10);
  }

  @Test
  public void calculateRequiredValidatorQuorum20Validator() {
    Assertions.assertThat(BftHelpers.calculateRequiredValidatorQuorum(20)).isEqualTo(14);
  }
}
