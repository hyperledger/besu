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

package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.DEFAULT_PRIVACY;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.FLEXIBLE_PRIVACY;
import static org.hyperledger.besu.ethereum.mainnet.MainnetPrecompiledContractRegistries.appendPrivacy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.FlexiblePrivacyPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.PrivacyPrecompiledContract;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MainnetPrecompiledContractRegistriesTest {
  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);
  private final GasCalculator gasCalculator = mock(GasCalculator.class);
  private final PrecompileContractRegistry reg = new PrecompileContractRegistry();

  private final PrecompiledContractConfiguration config =
      new PrecompiledContractConfiguration(gasCalculator, privacyParameters);

  @Test
  public void whenFlexiblePrivacyGroupsNotEnabled_defaultPrivacyPrecompileIsInRegistry() {
    when(privacyParameters.isFlexiblePrivacyGroupsEnabled()).thenReturn(false);
    when(privacyParameters.isEnabled()).thenReturn(true);

    appendPrivacy(reg, config);
    verify(privacyParameters).isEnabled();
    verify(privacyParameters).isFlexiblePrivacyGroupsEnabled();

    assertThat(reg.get(DEFAULT_PRIVACY)).isInstanceOf(PrivacyPrecompiledContract.class);
    assertThat(reg.get(FLEXIBLE_PRIVACY)).isNull();
  }

  @Test
  public void whenFlexiblePrivacyGroupsEnabled_flexiblePrivacyPrecompileIsInRegistry() {
    when(privacyParameters.isFlexiblePrivacyGroupsEnabled()).thenReturn(true);
    when(privacyParameters.isEnabled()).thenReturn(true);

    appendPrivacy(reg, config);
    verify(privacyParameters).isEnabled();
    verify(privacyParameters).isFlexiblePrivacyGroupsEnabled();

    assertThat(reg.get(FLEXIBLE_PRIVACY)).isInstanceOf(FlexiblePrivacyPrecompiledContract.class);
    assertThat(reg.get(DEFAULT_PRIVACY)).isNull();
  }

  @Test
  public void whenPrivacyNotEnabled_noPrivacyPrecompileInRegistry() {
    when(privacyParameters.isEnabled()).thenReturn(false);

    appendPrivacy(reg, config);
    verify(privacyParameters).isEnabled();
    verifyNoMoreInteractions(privacyParameters);

    assertThat(reg.get(FLEXIBLE_PRIVACY)).isNull();
    assertThat(reg.get(DEFAULT_PRIVACY)).isNull();
  }
}
