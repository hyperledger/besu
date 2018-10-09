package net.consensys.pantheon.consensus.common;

import java.util.Optional;

public enum VoteType {
  ADD(0x0L),
  DROP(0xFFFFFFFFFFFFFFFFL);

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
}
