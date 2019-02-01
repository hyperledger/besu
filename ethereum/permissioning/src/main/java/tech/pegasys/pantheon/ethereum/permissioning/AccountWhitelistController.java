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

import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class AccountWhitelistController {

  private static final int ACCOUNT_BYTES_SIZE = 20;
  private final List<String> accountWhitelist = new ArrayList<>();
  private boolean isAccountWhitelistSet = false;

  public AccountWhitelistController(final PermissioningConfiguration configuration) {
    if (configuration != null && configuration.isAccountWhitelistSet()) {
      this.isAccountWhitelistSet = configuration.isAccountWhitelistSet();
      if (!configuration.getAccountWhitelist().isEmpty()) {
        addAccounts(configuration.getAccountWhitelist());
      }
    }
  }

  public WhitelistOperationResult addAccounts(final List<String> accounts) {
    final WhitelistOperationResult inputValidationResult = inputValidation(accounts);
    if (inputValidationResult != WhitelistOperationResult.SUCCESS) {
      return inputValidationResult;
    }

    boolean inputHasExistingAccount = accounts.stream().anyMatch(accountWhitelist::contains);
    if (inputHasExistingAccount) {
      return WhitelistOperationResult.ERROR_EXISTING_ENTRY;
    }

    this.isAccountWhitelistSet = true;
    this.accountWhitelist.addAll(accounts);
    return WhitelistOperationResult.SUCCESS;
  }

  public WhitelistOperationResult removeAccounts(final List<String> accounts) {
    final WhitelistOperationResult inputValidationResult = inputValidation(accounts);
    if (inputValidationResult != WhitelistOperationResult.SUCCESS) {
      return inputValidationResult;
    }

    if (!accountWhitelist.containsAll(accounts)) {
      return WhitelistOperationResult.ERROR_ABSENT_ENTRY;
    }

    this.accountWhitelist.removeAll(accounts);
    return WhitelistOperationResult.SUCCESS;
  }

  private WhitelistOperationResult inputValidation(final List<String> accounts) {
    if (accounts == null || accounts.isEmpty()) {
      return WhitelistOperationResult.ERROR_EMPTY_ENTRY;
    }

    if (containsInvalidAccount(accounts)) {
      return WhitelistOperationResult.ERROR_INVALID_ENTRY;
    }

    if (inputHasDuplicates(accounts)) {
      return WhitelistOperationResult.ERROR_DUPLICATED_ENTRY;
    }

    return WhitelistOperationResult.SUCCESS;
  }

  private boolean inputHasDuplicates(final List<String> accounts) {
    return !accounts.stream().allMatch(new HashSet<>()::add);
  }

  public boolean contains(final String account) {
    return (!isAccountWhitelistSet || accountWhitelist.contains(account));
  }

  public boolean isAccountWhiteListSet() {
    return isAccountWhitelistSet;
  }

  public List<String> getAccountWhitelist() {
    return new ArrayList<>(accountWhitelist);
  }

  private boolean containsInvalidAccount(final List<String> accounts) {
    return !accounts.stream().allMatch(this::isValidAccountString);
  }

  private boolean isValidAccountString(final String account) {
    try {
      BytesValue bytesValue = BytesValue.fromHexString(account);
      return bytesValue.size() == ACCOUNT_BYTES_SIZE;
    } catch (NullPointerException | IndexOutOfBoundsException | IllegalArgumentException e) {
      return false;
    }
  }
}
