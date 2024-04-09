package org.hyperledger.besu.ethereum.trie.verkle;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

// TODO Maybe create a Bytes31 in Tuweni for stems?
public record VerkleProof(
    List<Bytes> otherStems,
    Bytes depthExtensionPresent,
    List<Bytes32> commitmentsByPath,
    Bytes32 d,
    IPAProof ipaProof) {

  @Override
  public String toString() {
    return "VerkleProof{"
        + "otherStems="
        + otherStems
        + ", depthExtensionPresent="
        + depthExtensionPresent
        + ", commitmentsByPath="
        + commitmentsByPath
        + ", d="
        + d
        + ", ipaProof="
        + ipaProof
        + '}';
  }
}
