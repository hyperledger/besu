/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.util;

import org.hyperledger.besu.datatypes.Wei;

import java.util.Map;
import java.util.Optional;

// similar to AccountDiff
// BUT
// there are more fields that need to be added
// stateDiff
// movePrecompileToAddress
public class AccountOverride {
  private final Wei balance;
  private final Optional<Long> nonce;
  private final Optional<String> code;
  private final Optional<Map<String, String>> state;

  private AccountOverride(final Builder builder) {
    this.balance = builder.balance;
    this.nonce = Optional.ofNullable(builder.nonce);
    this.code = Optional.ofNullable(builder.code);
    this.state = Optional.ofNullable(builder.state);
  }

  public Wei getBalance() {
    return balance;
  }

  public Optional<Long> getNonce() {
    return nonce;
  }

  public Optional<String> getCode() {
    return code;
  }

  public Optional<Map<String, String>> getState() {
    return state;
  }

  public static class Builder {
    private Wei balance;
    private Long nonce;
    private String code;
    private Map<String, String> state;

    public Builder balance(final Wei balance) {
      this.balance = balance;
      return this;
    }

    public Builder nonce(final Long nonce) {
      this.nonce = nonce;
      return this;
    }

    public Builder code(final String code) {
      this.code = code;
      return this;
    }

    public Builder stateDiff(final Map<String, String> state) {
      this.state = state;
      return this;
    }

    public AccountOverride build() {
      return new AccountOverride(this);
    }
  }
}
