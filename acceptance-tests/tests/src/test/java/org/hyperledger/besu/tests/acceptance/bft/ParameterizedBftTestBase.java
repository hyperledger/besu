/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.tests.acceptance.bft;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;

import java.util.Collection;

import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
@Ignore("This is not a test class, it offers BFT parameterization only.")
public abstract class ParameterizedBftTestBase extends AcceptanceTestBase {

  protected final String bftType;
  protected final BftAcceptanceTestParameterization nodeFactory;

  @Parameters(name = "{0}")
  public static Collection<Object[]> factoryFunctions() {
    return BftAcceptanceTestParameterization.getFactories();
  }

  protected ParameterizedBftTestBase(
      final String bftType, final BftAcceptanceTestParameterization input) {
    this.bftType = bftType;
    this.nodeFactory = input;
  }
}
