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
import java.util.List;

public class AccountWhitelistController {

  private static final int ACCOUNT_BYTES_SIZE = 20;
  private final List<String> accountWhitelist = new ArrayList<>();
  private boolean isAccountWhitelistSet = false;

  public AccountWhitelistController(final PermissioningConfiguration configuration) {
    if (configuration != null && configuration.isAccountWhitelistSet()) {
      addAccounts(configuration.getAccountWhitelist());
    }
  }

  public AddResult addAccounts(final List<String> accounts) {
    if (containsInvalidAccount(accounts)) {
      return AddResult.ERROR_INVALID_ENTRY;
    }

    boolean hasDuplicatedAccount = accounts.stream().anyMatch(accountWhitelist::contains);
    if (hasDuplicatedAccount) {
      return AddResult.ERROR_DUPLICATED_ENTRY;
    }

    this.isAccountWhitelistSet = true;
    this.accountWhitelist.addAll(accounts);
    return AddResult.SUCCESS;
  }

  public RemoveResult removeAccounts(final List<String> accounts) {
    if (containsInvalidAccount(accounts)) {
      return RemoveResult.ERROR_INVALID_ENTRY;
    }

    if (!accountWhitelist.containsAll(accounts)) {
      return RemoveResult.ERROR_ABSENT_ENTRY;
    }

    this.accountWhitelist.removeAll(accounts);
    return RemoveResult.SUCCESS;
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

  public enum AddResult {
    SUCCESS,
    ERROR_DUPLICATED_ENTRY,
    ERROR_INVALID_ENTRY
  }

  public enum RemoveResult {
    SUCCESS,
    ERROR_ABSENT_ENTRY,
    ERROR_INVALID_ENTRY
  }
}
