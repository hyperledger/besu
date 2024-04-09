package org.hyperledger.besu.ethereum.trie.verkle;

import java.util.List;

public record StateDiff(List<StemStateDiff> steamStateDiff) {

  @Override
  public String toString() {
    return "StateDiff{" + "steamStateDiff=" + steamStateDiff + '}';
  }
}
