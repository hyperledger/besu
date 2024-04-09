package org.hyperledger.besu.ethereum.trie.verkle;

import org.hyperledger.besu.ethereum.rlp.RLPInput;

public class ExecutionWitness {

  private final StateDiff stateDiff;
  private final VerkleProof verkleProof;

  // No-op contructor for testing
  public ExecutionWitness() {
    this.stateDiff = null;
    this.verkleProof = null;
  }

  public ExecutionWitness(final StateDiff stateDiff, final VerkleProof verkleProof) {
    this.stateDiff = stateDiff;
    this.verkleProof = verkleProof;
  }

  @SuppressWarnings("unused")
  public static ExecutionWitness readFrom(final RLPInput input) {
    return new ExecutionWitness();
  }

  @Override
  public String toString() {
    return "ExecutionWitness{" + "stateDiff=" + stateDiff + ", verkleProof=" + verkleProof + '}';
  }

  public StateDiff getStateDiff() {
    return stateDiff;
  }

  public VerkleProof getVerkleProof() {
    return verkleProof;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ExecutionWitness that = (ExecutionWitness) o;

    if (!stateDiff.equals(that.stateDiff)) return false;
    return verkleProof.equals(that.verkleProof);
  }

  @Override
  public int hashCode() {
    int result = stateDiff.hashCode();
    result = 31 * result + verkleProof.hashCode();
    return result;
  }
}
