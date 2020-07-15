/*
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.besu.ethereum.mainnet.MainnetPrecompiledContractRegistries.appendPrivacy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.OnChainPrivacyPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.PrivacyPrecompiledContract;
import org.hyperledger.besu.ethereum.vm.GasCalculator;

import org.junit.Test;

public class MainnetPrecompiledContractRegistriesTest {
  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);
  private final GasCalculator gasCalculator = mock(GasCalculator.class);
  private final PrecompileContractRegistry reg = new PrecompileContractRegistry();

  private final PrecompiledContractConfiguration config =
      new PrecompiledContractConfiguration(gasCalculator, privacyParameters);

  @Test
  public void whenOnchainPrivacyGroupsNotEnabled_defaultPrivacyPrecompileIsInRegistry() {
    when(privacyParameters.isOnchainPrivacyGroupsEnabled()).thenReturn(false);
    when(privacyParameters.isEnabled()).thenReturn(true);

    appendPrivacy(reg, config, Account.DEFAULT_VERSION);
    verify(privacyParameters).isEnabled();
    verify(privacyParameters).isOnchainPrivacyGroupsEnabled();

    assertThat(reg.get(Address.DEFAULT_PRIVACY, Account.DEFAULT_VERSION))
        .isInstanceOf(PrivacyPrecompiledContract.class);
    assertThat(reg.get(Address.ONCHAIN_PRIVACY, Account.DEFAULT_VERSION)).isNull();
  }

  @Test
  public void whenOnchainPrivacyGroupsEnabled_onchainPrivacyPrecompileIsInRegistry() {
    when(privacyParameters.isOnchainPrivacyGroupsEnabled()).thenReturn(true);
    when(privacyParameters.isEnabled()).thenReturn(true);

    appendPrivacy(reg, config, Account.DEFAULT_VERSION);
    verify(privacyParameters).isEnabled();
    verify(privacyParameters).isOnchainPrivacyGroupsEnabled();

    assertThat(reg.get(Address.ONCHAIN_PRIVACY, Account.DEFAULT_VERSION))
        .isInstanceOf(OnChainPrivacyPrecompiledContract.class);
    assertThat(reg.get(Address.DEFAULT_PRIVACY, Account.DEFAULT_VERSION)).isNull();
  }

  @Test
  public void whenPrivacyNotEnabled_noPrivacyPrecompileInRegistry() {
    when(privacyParameters.isEnabled()).thenReturn(false);

    appendPrivacy(reg, config, Account.DEFAULT_VERSION);
    verify(privacyParameters).isEnabled();
    verifyNoMoreInteractions(privacyParameters);

    assertThat(reg.get(Address.ONCHAIN_PRIVACY, Account.DEFAULT_VERSION)).isNull();
    assertThat(reg.get(Address.DEFAULT_PRIVACY, Account.DEFAULT_VERSION)).isNull();
  }
}
