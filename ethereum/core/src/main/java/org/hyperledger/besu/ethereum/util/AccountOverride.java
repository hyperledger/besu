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

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

// similar to AccountDiff
// BUT
// there are more fields that need to be added
// stateDiff
// movePrecompileToAddress
@JsonDeserialize(builder = AccountOverride.Builder.class)
public class AccountOverride {
  private final Optional<Wei> balance;
  private final Optional<Long> nonce;
  private final Optional<String> code;
  private final Optional<Map<String, String>> state;

  private AccountOverride(
      final Optional<Wei> balance,
      final Optional<Long> nonce,
      final Optional<String> code,
      final Optional<Map<String, String>> state) {
    this.balance = balance;
    this.nonce = nonce;
    this.code = code;
    this.state = state;
  }

  @JsonGetter("balance")
  public Optional<Wei> getBalance() {
    return balance;
  }

  @JsonGetter("nonce")
  public Optional<Long> getNonce() {
    return nonce;
  }

  @JsonGetter("code")
  public Optional<String> getCode() {
    return code;
  }

  @JsonGetter("state")
  public Optional<Map<String, String>> getState() {
    return state;
  }

  public static class Builder {
    private Optional<Wei> balance = Optional.empty();
    private Optional<Long> nonce = Optional.empty();
    private Optional<String> code = Optional.empty();
    private Optional<Map<String, String>> state = Optional.empty();

    /** Default constructor. */
    public Builder() {}

    public Builder balance(final Wei balance) {
      this.balance = Optional.ofNullable(balance);
      return this;
    }

    public Builder nonce(final Long nonce) {
      this.nonce = Optional.ofNullable(nonce);
      return this;
    }

    public Builder code(final String code) {
      this.code = Optional.ofNullable(code);
      return this;
    }

    public Builder stateDiff(final Map<String, String> state) {
      this.state = Optional.ofNullable(state);
      return this;
    }

    public AccountOverride build() {
      return new AccountOverride(balance, nonce, code, state);
    }
  }

  @Override
  public String toString() {
    return "AccountOverride{"
        + "balance="
        + balance
        + ", nonce="
        + nonce
        + ", code="
        + code
        + ", state="
        + state
        + '}';
  }
}
