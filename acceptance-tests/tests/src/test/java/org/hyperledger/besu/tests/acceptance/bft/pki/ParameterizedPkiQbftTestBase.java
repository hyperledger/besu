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
package org.hyperledger.besu.tests.acceptance.bft.pki;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBaseJunit5;

import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.provider.Arguments;

@Disabled("This is not a test class, it offers PKI QBFT parameterization only.")
public abstract class ParameterizedPkiQbftTestBase extends AcceptanceTestBaseJunit5 {
  public static Stream<Arguments> factoryFunctions() {
    return PkiQbftAcceptanceTestParameterization.getFactories();
  }
}
