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
package org.hyperledger.besu.ethereum.permissioning.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.TransactionSmartContractPermissioningController;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AccountPermissioningControllerTest {

  private AccountPermissioningController permissioningController;

  @Mock private AccountLocalConfigPermissioningController localConfigController;
  @Mock private TransactionSmartContractPermissioningController smartContractController;

  @Before
  public void before() {
    permissioningController =
        new AccountPermissioningController(
            Optional.of(localConfigController), Optional.of(smartContractController));
  }

  @Test
  public void shouldOnlyCheckLocalConfigControllerWhenNotPersistingState() {
    when(localConfigController.isPermitted(any())).thenReturn(true);

    boolean isPermitted = permissioningController.isPermitted(mock(Transaction.class), false);

    assertThat(isPermitted).isTrue();

    verify(localConfigController).isPermitted(any());
    verifyZeroInteractions(smartContractController);
  }

  @Test
  public void shouldOnlyCheckLocalConfigAndSmartContractControllerWhenPersistingState() {
    when(localConfigController.isPermitted(any())).thenReturn(true);
    when(smartContractController.isPermitted(any())).thenReturn(true);

    boolean isPermitted = permissioningController.isPermitted(mock(Transaction.class), true);

    assertThat(isPermitted).isTrue();

    verify(localConfigController).isPermitted(any());
    verify(smartContractController).isPermitted(any());
  }

  @Test
  public void shouldReturnFalseIfOneControllerPermitsAndTheOtherDoesNot() {
    when(localConfigController.isPermitted(any())).thenReturn(true);
    when(smartContractController.isPermitted(any())).thenReturn(false);

    boolean isPermitted = permissioningController.isPermitted(mock(Transaction.class), true);

    assertThat(isPermitted).isFalse();

    verify(localConfigController).isPermitted(any());
    verify(smartContractController).isPermitted(any());
  }
}
