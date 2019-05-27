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
  private final boolean stateChange;

  private TransactionValidationParams(final boolean allowFutureNonce, final boolean stateChange) {
    this.allowFutureNonce = allowFutureNonce;
    this.stateChange = stateChange;
  }

  public boolean isAllowFutureNonce() {
    return allowFutureNonce;
  }

  public boolean isStateChange() {
    return stateChange;
  }

  public static class Builder {

    private boolean allowFutureNonce = false;
    private boolean stateChange = false;

    public Builder allowFutureNonce(final boolean allowFutureNonce) {
      this.allowFutureNonce = allowFutureNonce;
      return this;
    }

    public Builder stateChange(final boolean stateChange) {
      this.stateChange = stateChange;
      return this;
    }

    public TransactionValidationParams build() {
      return new TransactionValidationParams(allowFutureNonce, stateChange);
    }
  }
}
