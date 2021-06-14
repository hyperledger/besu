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
package org.hyperledger.besu.tests.acceptance.dsl.privacy;

import org.hyperledger.enclave.testutil.EnclaveType;

import java.util.Arrays;
import java.util.Collection;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public abstract class ParameterizedEnclaveTestBase extends PrivacyAcceptanceTestBase {
  protected final EnclaveType enclaveType;

  protected ParameterizedEnclaveTestBase(final EnclaveType enclaveType) {
    this.enclaveType = enclaveType;
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<EnclaveType> enclaveTypes() {
    return Arrays.asList(EnclaveType.values());
  }
}
