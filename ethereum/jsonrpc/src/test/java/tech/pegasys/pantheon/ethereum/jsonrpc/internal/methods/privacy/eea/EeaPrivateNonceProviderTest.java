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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy.eea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.EnclaveException;
import tech.pegasys.pantheon.enclave.types.FindPrivacyGroupRequest;
import tech.pegasys.pantheon.enclave.types.PrivacyGroup;
import tech.pegasys.pantheon.enclave.types.PrivacyGroup.Type;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.privacy.methods.eea.EeaPrivateNonceProvider;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionHandler;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class EeaPrivateNonceProviderTest {

  private final Address address = Address.fromHexString("55");
  private Enclave enclave = mock(Enclave.class);
  private PrivateTransactionHandler privateTransactionHandler =
      mock(PrivateTransactionHandler.class);

  private final EeaPrivateNonceProvider nonceProvider =
      new EeaPrivateNonceProvider(enclave, privateTransactionHandler);

  @Test
  public void validRequestProducesExpectedNonce() {
    final long reportedNonce = 8L;
    PrivacyGroup[] returnedGroups =
        new PrivacyGroup[] {
          new PrivacyGroup("Group1", Type.LEGACY, "Group1_Name", "Group1_Desc", new String[0]),
        };

    final ArgumentCaptor<FindPrivacyGroupRequest> groupMembersCaptor =
        ArgumentCaptor.forClass(FindPrivacyGroupRequest.class);

    when(enclave.findPrivacyGroup(groupMembersCaptor.capture())).thenReturn(returnedGroups);
    when(privateTransactionHandler.getSenderNonce(address, "Group1")).thenReturn(reportedNonce);

    final long nonce =
        nonceProvider.determineNonce("privateFrom", new String[] {"first", "second"}, address);

    assertThat(nonce).isEqualTo(reportedNonce);
    assertThat(groupMembersCaptor.getValue().addresses())
        .containsAll(Lists.newArrayList("privateFrom", "first", "second"));
  }

  @Test
  public void noMatchingLegacyGroupsProducesExpectedNonce() {
    final long reportedNonce = 0L;
    PrivacyGroup[] returnedGroups = new PrivacyGroup[0];

    final ArgumentCaptor<FindPrivacyGroupRequest> groupMembersCaptor =
        ArgumentCaptor.forClass(FindPrivacyGroupRequest.class);

    when(enclave.findPrivacyGroup(groupMembersCaptor.capture())).thenReturn(returnedGroups);
    when(privateTransactionHandler.getSenderNonce(address, "Group1")).thenReturn(reportedNonce);

    final long nonce =
        nonceProvider.determineNonce("privateFrom", new String[] {"first", "second"}, address);

    assertThat(nonce).isEqualTo(reportedNonce);
    assertThat(groupMembersCaptor.getValue().addresses())
        .containsAll(Lists.newArrayList("privateFrom", "first", "second"));
  }

  @Test
  public void moreThanOneMatchingLegacyGroupThrowsException() {
    PrivacyGroup[] returnedGroups =
        new PrivacyGroup[] {
          new PrivacyGroup("Group1", Type.LEGACY, "Group1_Name", "Group1_Desc", new String[0]),
          new PrivacyGroup("Group2", Type.LEGACY, "Group2_Name", "Group2_Desc", new String[0]),
        };

    when(enclave.findPrivacyGroup(any())).thenReturn(returnedGroups);

    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(
            () ->
                nonceProvider.determineNonce(
                    "privateFrom", new String[] {"first", "second"}, address));
  }

  @Test
  public void enclaveThrowingAnExceptionResultsInErrorResponse() {
    when(enclave.findPrivacyGroup(any())).thenThrow(new EnclaveException("Enclave Failed"));
    assertThatExceptionOfType(EnclaveException.class)
        .isThrownBy(
            () ->
                nonceProvider.determineNonce(
                    "privateFrom", new String[] {"first", "second"}, address));
  }
}
