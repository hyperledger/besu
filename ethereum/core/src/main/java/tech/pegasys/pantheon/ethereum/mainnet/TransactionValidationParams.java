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
package tech.pegasys.pantheon.ethereum.mainnet;

public class TransactionValidationParams {

  private final boolean allowFutureNonce;
  private final boolean checkOnchainPermissions;
  private final boolean checkLocalPermissions;

  private TransactionValidationParams(
      final boolean allowFutureNonce,
      final boolean checkOnchainPermissions,
      final boolean checkLocalPermissions) {
    this.allowFutureNonce = allowFutureNonce;
    this.checkOnchainPermissions = checkOnchainPermissions;
    this.checkLocalPermissions = checkLocalPermissions;
  }

  public boolean isAllowFutureNonce() {
    return allowFutureNonce;
  }

  public boolean checkOnchainPermissions() {
    return checkOnchainPermissions;
  }

  public boolean checkLocalPermissions() {
    return checkLocalPermissions;
  }

  public static TransactionValidationParams transactionSimulator() {
    return new Builder()
        .checkLocalPermissions(false)
        .checkOnchainPermissions(false)
        .allowFutureNonce(false)
        .build();
  }

  public static TransactionValidationParams processingBlock() {
    return new Builder()
        .checkLocalPermissions(false)
        .checkOnchainPermissions(true)
        .allowFutureNonce(false)
        .build();
  }

  public static TransactionValidationParams transactionPool() {
    return new Builder()
        .checkLocalPermissions(true)
        .checkOnchainPermissions(false)
        .allowFutureNonce(true)
        .build();
  }

  public static TransactionValidationParams mining() {
    return new Builder()
        .checkLocalPermissions(true)
        .checkOnchainPermissions(true)
        .allowFutureNonce(false)
        .build();
  }

  public static TransactionValidationParams blockReplay() {
    return new Builder()
        .checkLocalPermissions(false)
        .checkOnchainPermissions(false)
        .allowFutureNonce(false)
        .build();
  }

  static class Builder {

    private boolean allowFutureNonce = false;
    private boolean checkOnchainPermissions = false;
    private boolean checkLocalPermissions = true;

    public Builder allowFutureNonce(final boolean allowFutureNonce) {
      this.allowFutureNonce = allowFutureNonce;
      return this;
    }

    public Builder checkOnchainPermissions(final boolean checkOnchainPermissions) {
      this.checkOnchainPermissions = checkOnchainPermissions;
      return this;
    }

    public Builder checkLocalPermissions(final boolean checkLocalPermissions) {
      this.checkLocalPermissions = checkLocalPermissions;
      return this;
    }

    public TransactionValidationParams build() {
      return new TransactionValidationParams(
          allowFutureNonce, checkOnchainPermissions, checkLocalPermissions);
    }
  }
}
