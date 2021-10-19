/*
 * Copyright Hyperledger Besu Contributors.
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

package org.hyperledger.besu.tests.acceptance.bft.qbft;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;

import java.util.Collection;

import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
@Ignore("This is not a test class, it offers parameterization only.")
public abstract class ParameterizedQbftContractBasedVotingTestBase extends AcceptanceTestBase {
  protected final QbftContractBasedParameterizedFactoryProvider factoryProvider;

  protected ParameterizedQbftContractBasedVotingTestBase(
      final String testName, final QbftContractBasedParameterizedFactoryProvider factoryProvider) {
    this.factoryProvider = factoryProvider;
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> factoryFunctions() {
    return QbftContractBasedParameterizedFactoryProvider.getFactories();
  }
}
