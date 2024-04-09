package org.hyperledger.besu.ethereum.trie.verkle;

import java.util.List;

import org.apache.tuweni.bytes.Bytes32;

public record IPAProof(List<Bytes32> cl, List<Bytes32> cr, Bytes32 finalEvaluation) {
  private static final int IPA_PROOF_DEPTH = 8;

  public IPAProof(final List<Bytes32> cl, final List<Bytes32> cr, final Bytes32 finalEvaluation) {
    if (cl.size() != IPA_PROOF_DEPTH || cr.size() != IPA_PROOF_DEPTH) {
      throw new IllegalArgumentException("cl and cr must have a length of " + IPA_PROOF_DEPTH);
    }
    this.cl = cl;
    this.cr = cr;
    this.finalEvaluation = finalEvaluation;
  }

  @Override
  public String toString() {
    return "IPAProof{"
        + "cl="
        + cl.toString()
        + ", cr="
        + cr.toString()
        + ", finalEvaluation="
        + finalEvaluation
        + '}';
  }
}
