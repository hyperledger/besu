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
package tech.pegasys.pantheon.consensus.common;

import java.util.Optional;

public enum VoteType implements ValidatorVote {
  ADD(0xFFFFFFFFFFFFFFFFL),
  DROP(0x0L);

  private final long nonceValue;

  VoteType(final long nonceValue) {
    this.nonceValue = nonceValue;
  }

  public long getNonceValue() {
    return nonceValue;
  }

  public static Optional<VoteType> fromNonce(final long nonce) {
    for (final VoteType voteType : values()) {
      if (Long.compareUnsigned(voteType.nonceValue, nonce) == 0) {
        return Optional.of(voteType);
      }
    }
    return Optional.empty();
  }

  @Override
  public boolean isAddVote() {
    return this.equals(ADD);
  }

  @Override
  public boolean isDropVote() {
    return this.equals(DROP);
  }
}
