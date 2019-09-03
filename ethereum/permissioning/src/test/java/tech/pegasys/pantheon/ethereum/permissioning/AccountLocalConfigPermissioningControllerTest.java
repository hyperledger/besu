/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.permissioning;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.plugin.services.metrics.Counter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AccountLocalConfigPermissioningControllerTest {

  private AccountLocalConfigPermissioningController controller;
  @Mock private LocalPermissioningConfiguration permissioningConfig;
  @Mock private WhitelistPersistor whitelistPersistor;
  @Mock private MetricsSystem metricsSystem;
  @Mock private Counter checkCounter;
  @Mock private Counter checkPermittedCounter;
  @Mock private Counter checkUnpermittedCounter;

  @Before
  public void before() {

    when(metricsSystem.createCounter(
            PantheonMetricCategory.PERMISSIONING,
            "account_local_check_count",
            "Number of times the account local permissioning provider has been checked"))
        .thenReturn(checkCounter);

    when(metricsSystem.createCounter(
            PantheonMetricCategory.PERMISSIONING,
            "account_local_check_count_permitted",
            "Number of times the account local permissioning provider has been checked and returned permitted"))
        .thenReturn(checkPermittedCounter);

    when(metricsSystem.createCounter(
            PantheonMetricCategory.PERMISSIONING,
            "account_local_check_count_unpermitted",
            "Number of times the account local permissioning provider has been checked and returned unpermitted"))
        .thenReturn(checkUnpermittedCounter);

    controller =
        new AccountLocalConfigPermissioningController(
            permissioningConfig, whitelistPersistor, metricsSystem);
  }

  @Test
  public void whenPermConfigHasAccountsShouldAddAllAccountsToWhitelist() {
    when(permissioningConfig.isAccountWhitelistEnabled()).thenReturn(true);
    when(permissioningConfig.getAccountWhitelist())
        .thenReturn(singletonList("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));
    controller =
        new AccountLocalConfigPermissioningController(
            permissioningConfig, whitelistPersistor, metricsSystem);

    assertThat(controller.getAccountWhitelist())
        .contains("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");
  }

  @Test
  public void whenLoadingAccountsFromConfigShouldNormalizeAccountsToLowerCase() {
    when(permissioningConfig.isAccountWhitelistEnabled()).thenReturn(true);
    when(permissioningConfig.getAccountWhitelist())
        .thenReturn(singletonList("0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"));
    controller =
        new AccountLocalConfigPermissioningController(
            permissioningConfig, whitelistPersistor, metricsSystem);

    assertThat(controller.getAccountWhitelist())
        .containsExactly("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");
  }

  @Test
  public void whenPermConfigContainsEmptyListOfAccountsContainsShouldReturnFalse() {
    when(permissioningConfig.isAccountWhitelistEnabled()).thenReturn(true);
    when(permissioningConfig.getAccountWhitelist()).thenReturn(new ArrayList<>());
    controller =
        new AccountLocalConfigPermissioningController(
            permissioningConfig, whitelistPersistor, metricsSystem);

    assertThat(controller.contains("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73")).isFalse();
  }

  @Test
  public void addAccountsWithInvalidAccountShouldReturnInvalidEntryResult() {
    WhitelistOperationResult addResult = controller.addAccounts(Arrays.asList("0x0"));

    assertThat(addResult).isEqualTo(WhitelistOperationResult.ERROR_INVALID_ENTRY);
    assertThat(controller.getAccountWhitelist()).isEmpty();
  }

  @Test
  public void addExistingAccountShouldReturnExistingEntryResult() {
    controller.addAccounts(Arrays.asList("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));
    WhitelistOperationResult addResult =
        controller.addAccounts(Arrays.asList("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));

    assertThat(addResult).isEqualTo(WhitelistOperationResult.ERROR_EXISTING_ENTRY);
    assertThat(controller.getAccountWhitelist())
        .containsExactly("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");
  }

  @Test
  public void addExistingAccountWithDifferentCasingShouldReturnExistingEntryResult() {
    controller.addAccounts(Arrays.asList("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));
    WhitelistOperationResult addResult =
        controller.addAccounts(Arrays.asList("0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"));

    assertThat(addResult).isEqualTo(WhitelistOperationResult.ERROR_EXISTING_ENTRY);
    assertThat(controller.getAccountWhitelist())
        .containsExactly("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");
  }

  @Test
  public void addValidAccountsShouldReturnSuccessResult() {
    WhitelistOperationResult addResult =
        controller.addAccounts(Arrays.asList("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));

    assertThat(addResult).isEqualTo(WhitelistOperationResult.SUCCESS);
    assertThat(controller.getAccountWhitelist())
        .containsExactly("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");
  }

  @Test
  public void addAccountsShouldAddAccountNormalizedToLowerCase() {
    WhitelistOperationResult addResult =
        controller.addAccounts(Arrays.asList("0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"));

    assertThat(addResult).isEqualTo(WhitelistOperationResult.SUCCESS);
    assertThat(controller.getAccountWhitelist())
        .containsExactly("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");
  }

  @Test
  public void removeExistingAccountShouldReturnSuccessResult() {
    controller.addAccounts(Arrays.asList("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));

    WhitelistOperationResult removeResult =
        controller.removeAccounts(Arrays.asList("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));

    assertThat(removeResult).isEqualTo(WhitelistOperationResult.SUCCESS);
    assertThat(controller.getAccountWhitelist()).isEmpty();
  }

  @Test
  public void removeAccountShouldNormalizeAccountToLowerCAse() {
    controller.addAccounts(Arrays.asList("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));

    WhitelistOperationResult removeResult =
        controller.removeAccounts(Arrays.asList("0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"));

    assertThat(removeResult).isEqualTo(WhitelistOperationResult.SUCCESS);
    assertThat(controller.getAccountWhitelist()).isEmpty();
  }

  @Test
  public void removeAbsentAccountShouldReturnAbsentEntryResult() {
    WhitelistOperationResult removeResult =
        controller.removeAccounts(Arrays.asList("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));

    assertThat(removeResult).isEqualTo(WhitelistOperationResult.ERROR_ABSENT_ENTRY);
    assertThat(controller.getAccountWhitelist()).isEmpty();
  }

  @Test
  public void removeInvalidAccountShouldReturnInvalidEntryResult() {
    WhitelistOperationResult removeResult = controller.removeAccounts(Arrays.asList("0x0"));

    assertThat(removeResult).isEqualTo(WhitelistOperationResult.ERROR_INVALID_ENTRY);
    assertThat(controller.getAccountWhitelist()).isEmpty();
  }

  @Test
  public void addDuplicatedAccountShouldReturnDuplicatedEntryResult() {
    WhitelistOperationResult addResult =
        controller.addAccounts(
            Arrays.asList(
                "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
                "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));

    assertThat(addResult).isEqualTo(WhitelistOperationResult.ERROR_DUPLICATED_ENTRY);
  }

  @Test
  public void removeDuplicatedAccountShouldReturnDuplicatedEntryResult() {
    WhitelistOperationResult removeResult =
        controller.removeAccounts(
            Arrays.asList(
                "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
                "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));

    assertThat(removeResult).isEqualTo(WhitelistOperationResult.ERROR_DUPLICATED_ENTRY);
  }

  @Test
  public void removeNullListShouldReturnEmptyEntryResult() {
    WhitelistOperationResult removeResult = controller.removeAccounts(null);

    assertThat(removeResult).isEqualTo(WhitelistOperationResult.ERROR_EMPTY_ENTRY);
  }

  @Test
  public void removeEmptyListShouldReturnEmptyEntryResult() {
    WhitelistOperationResult removeResult = controller.removeAccounts(new ArrayList<>());

    assertThat(removeResult).isEqualTo(WhitelistOperationResult.ERROR_EMPTY_ENTRY);
  }

  @Test
  public void stateShouldRevertIfWhitelistPersistFails()
      throws IOException, WhitelistFileSyncException {
    List<String> newAccount = singletonList("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");
    List<String> newAccount2 = singletonList("0xfe3b557e8fb62b89f4916b721be55ceb828dbd72");

    assertThat(controller.getAccountWhitelist().size()).isEqualTo(0);

    controller.addAccounts(newAccount);
    assertThat(controller.getAccountWhitelist().size()).isEqualTo(1);

    doThrow(new IOException()).when(whitelistPersistor).updateConfig(any(), any());
    controller.addAccounts(newAccount2);

    assertThat(controller.getAccountWhitelist().size()).isEqualTo(1);
    assertThat(controller.getAccountWhitelist()).isEqualTo(newAccount);

    verify(whitelistPersistor, times(3)).verifyConfigFileMatchesState(any(), any());
    verify(whitelistPersistor, times(2)).updateConfig(any(), any());
    verifyNoMoreInteractions(whitelistPersistor);
  }

  @Test
  public void reloadAccountWhitelistWithValidConfigFileShouldUpdateWhitelist() throws Exception {
    final String expectedAccount = "0x627306090abab3a6e1400e9345bc60c78a8bef57";
    final Path permissionsFile = createPermissionsFileWithAccount(expectedAccount);

    when(permissioningConfig.getAccountPermissioningConfigFilePath())
        .thenReturn(permissionsFile.toAbsolutePath().toString());
    when(permissioningConfig.isAccountWhitelistEnabled()).thenReturn(true);
    when(permissioningConfig.getAccountWhitelist())
        .thenReturn(Arrays.asList("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));
    controller =
        new AccountLocalConfigPermissioningController(
            permissioningConfig, whitelistPersistor, metricsSystem);

    controller.reload();

    assertThat(controller.getAccountWhitelist()).containsExactly(expectedAccount);
  }

  @Test
  public void reloadAccountWhitelistWithErrorReadingConfigFileShouldKeepOldWhitelist() {
    when(permissioningConfig.getAccountPermissioningConfigFilePath()).thenReturn("foo");
    when(permissioningConfig.isAccountWhitelistEnabled()).thenReturn(true);
    when(permissioningConfig.getAccountWhitelist())
        .thenReturn(Arrays.asList("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));
    controller =
        new AccountLocalConfigPermissioningController(
            permissioningConfig, whitelistPersistor, metricsSystem);

    final Throwable thrown = catchThrowable(() -> controller.reload());

    assertThat(thrown)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unable to read permissioning TOML config file");

    assertThat(controller.getAccountWhitelist())
        .containsExactly("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");
  }

  @Test
  public void accountThatDoesNotStartWith0xIsNotValid() {
    assertThat(AccountLocalConfigPermissioningController.isValidAccountString("bob")).isFalse();
    assertThat(
            AccountLocalConfigPermissioningController.isValidAccountString(
                "b9b81ee349c3807e46bc71aa2632203c5b462032"))
        .isFalse();
    assertThat(
            AccountLocalConfigPermissioningController.isValidAccountString(
                "0xb9b81ee349c3807e46bc71aa2632203c5b462032"))
        .isTrue();
  }

  @Test
  public void shouldMatchAccountsWithInconsistentCasing() {
    when(permissioningConfig.isAccountWhitelistEnabled()).thenReturn(true);
    when(permissioningConfig.getAccountWhitelist())
        .thenReturn(singletonList("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));
    controller =
        new AccountLocalConfigPermissioningController(
            permissioningConfig, whitelistPersistor, metricsSystem);

    assertThat(controller.contains("0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73")).isTrue();
  }

  @Test
  public void isPermittedShouldCheckIfAccountExistInTheWhitelist() {
    when(permissioningConfig.isAccountWhitelistEnabled()).thenReturn(true);
    when(permissioningConfig.getAccountWhitelist())
        .thenReturn(singletonList("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));
    controller =
        new AccountLocalConfigPermissioningController(
            permissioningConfig, whitelistPersistor, metricsSystem);

    final Transaction transaction = mock(Transaction.class);
    when(transaction.getSender())
        .thenReturn(Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));

    verifyCountersUntouched();

    boolean permitted = controller.isPermitted(transaction);

    assertThat(permitted).isTrue();

    verifyCountersPermitted();
  }

  @Test
  public void isPermittedShouldReturnFalseIfTransactionDoesNotContainSender() {
    final Transaction transactionWithoutSender = mock(Transaction.class);
    when(transactionWithoutSender.getSender()).thenReturn(null);

    verifyCountersUntouched();

    boolean permitted = controller.isPermitted(transactionWithoutSender);

    assertThat(permitted).isFalse();

    verifyCountersUnpermitted();
  }

  private Path createPermissionsFileWithAccount(final String account) throws IOException {
    final String nodePermissionsFileContent = "accounts-whitelist=[\"" + account + "\"]";
    final Path permissionsFile = Files.createTempFile("account_permissions", "");
    permissionsFile.toFile().deleteOnExit();
    Files.write(permissionsFile, nodePermissionsFileContent.getBytes(StandardCharsets.UTF_8));
    return permissionsFile;
  }

  private void verifyCountersUntouched() {
    verify(checkCounter, times(0)).inc();
    verify(checkPermittedCounter, times(0)).inc();
    verify(checkUnpermittedCounter, times(0)).inc();
  }

  private void verifyCountersPermitted() {
    verify(checkCounter, times(1)).inc();
    verify(checkPermittedCounter, times(1)).inc();
    verify(checkUnpermittedCounter, times(0)).inc();
  }

  private void verifyCountersUnpermitted() {
    verify(checkCounter, times(1)).inc();
    verify(checkPermittedCounter, times(0)).inc();
    verify(checkUnpermittedCounter, times(1)).inc();
  }
}
