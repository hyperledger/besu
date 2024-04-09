package org.hyperledger.besu.ethereum.trie.verkle;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;

public record StemStateDiff(Bytes stem, List<SuffixStateDiff> suffixDiffs) {

  @Override
  public String toString() {
    return "StemStateDiff{" + "stem=" + stem + ", suffixDiffs=" + suffixDiffs + '}';
  }
}
