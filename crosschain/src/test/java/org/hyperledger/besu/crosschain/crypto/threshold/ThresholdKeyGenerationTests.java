/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.crosschain.crypto.threshold;

import org.junit.Before;

// This is the main class for running through a simple scenario.
public class ThresholdKeyGenerationTests {
  public static class Scenario1_1 extends AbstractThresholdKeyGenerationTest {
    @Before
    public void genKeys() {
      generateKeys(1, 1);
    }
  }

  public static class Scenario2_1 extends AbstractThresholdKeyGenerationTest {
    @Before
    public void genKeys() {
      generateKeys(2, 1);
    }
  }

  public static class Scenario3_1 extends AbstractThresholdKeyGenerationTest {
    @Before
    public void genKeys() {
      generateKeys(3, 1);
    }
  }

  public static class Scenario2_2 extends AbstractThresholdKeyGenerationTest {
    @Before
    public void genKeys() {
      generateKeys(2, 2);
    }
  }

  public static class Scenario3_2 extends AbstractThresholdKeyGenerationTest {
    @Before
    public void genKeys() {
      generateKeys(3, 2);
    }
  }

  public static class Scenario3_3 extends AbstractThresholdKeyGenerationTest {
    @Before
    public void genKeys() {
      generateKeys(3, 3);
    }
  }

  public static class Scenario5_1 extends AbstractThresholdKeyGenerationTest {
    @Before
    public void genKeys() {
      generateKeys(5, 1);
    }
  }

  public static class Scenario5_3 extends AbstractThresholdKeyGenerationTest {
    @Before
    public void genKeys() {
      generateKeys(5, 3);
    }
  }

  public static class Scenario5_5 extends AbstractThresholdKeyGenerationTest {
    @Before
    public void genKeys() {
      generateKeys(5, 5);
    }
  }
}
