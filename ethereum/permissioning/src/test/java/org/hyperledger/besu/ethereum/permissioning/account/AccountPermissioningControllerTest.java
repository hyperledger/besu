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
package org.hyperledger.besu.ethereum.permissioning.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.TransactionSmartContractPermissioningController;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AccountPermissioningControllerTest {

  private AccountPermissioningController permissioningController;

  @Mock private AccountLocalConfigPermissioningController localConfigController;
  @Mock private TransactionSmartContractPermissioningController smartContractController;

  @BeforeEach
  public void before() {
    permissioningController =
        new AccountPermissioningController(
            Optional.of(localConfigController), Optional.of(smartContractController));
  }

  @Test
  public void shouldOnlyCheckLocalConfigControllerWhenNotPersistingState() {
    when(localConfigController.isPermitted(any())).thenReturn(true);

    boolean isPermitted = permissioningController.isPermitted(mock(Transaction.class), true, false);

    assertThat(isPermitted).isTrue();

    verify(localConfigController).isPermitted(any());
    verifyNoInteractions(smartContractController);
  }

  @Test
  public void shouldOnlyCheckLocalConfigAndSmartContractControllerWhenPersistingState() {
    when(localConfigController.isPermitted(any())).thenReturn(true);
    when(smartContractController.isPermitted(any())).thenReturn(true);

    boolean isPermitted = permissioningController.isPermitted(mock(Transaction.class), true, true);

    assertThat(isPermitted).isTrue();

    verify(localConfigController).isPermitted(any());
    verify(smartContractController).isPermitted(any());
  }

  @Test
  public void shouldReturnFalseIfOneControllerPermitsAndTheOtherDoesNot() {
    when(localConfigController.isPermitted(any())).thenReturn(true);
    when(smartContractController.isPermitted(any())).thenReturn(false);

    boolean isPermitted = permissioningController.isPermitted(mock(Transaction.class), true, true);

    assertThat(isPermitted).isFalse();

    verify(localConfigController).isPermitted(any());
    verify(smartContractController).isPermitted(any());
  }
}
