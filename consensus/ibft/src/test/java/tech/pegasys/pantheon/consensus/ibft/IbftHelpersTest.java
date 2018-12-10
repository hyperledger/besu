/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.ibft;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.pantheon.consensus.ibft.IbftHelpers.calculateRequiredValidatorQuorum;

import org.junit.Test;

public class IbftHelpersTest {

  @Test
  public void calculateRequiredValidatorQuorum1Validator() {
    assertThat(calculateRequiredValidatorQuorum(1)).isEqualTo(1);
  }

  @Test
  public void calculateRequiredValidatorQuorum2Validator() {
    assertThat(calculateRequiredValidatorQuorum(2)).isEqualTo(2);
  }

  @Test
  public void calculateRequiredValidatorQuorum3Validator() {
    assertThat(calculateRequiredValidatorQuorum(3)).isEqualTo(2);
  }

  @Test
  public void calculateRequiredValidatorQuorum4Validator() {
    assertThat(calculateRequiredValidatorQuorum(4)).isEqualTo(3);
  }

  @Test
  public void calculateRequiredValidatorQuorum5Validator() {
    assertThat(calculateRequiredValidatorQuorum(5)).isEqualTo(4);
  }

  @Test
  public void calculateRequiredValidatorQuorum7Validator() {
    assertThat(calculateRequiredValidatorQuorum(7)).isEqualTo(5);
  }

  @Test
  public void calculateRequiredValidatorQuorum10Validator() {
    assertThat(calculateRequiredValidatorQuorum(10)).isEqualTo(7);
  }

  @Test
  public void calculateRequiredValidatorQuorum15Validator() {
    assertThat(calculateRequiredValidatorQuorum(15)).isEqualTo(10);
  }

  @Test
  public void calculateRequiredValidatorQuorum20Validator() {
    assertThat(calculateRequiredValidatorQuorum(20)).isEqualTo(14);
  }
}
