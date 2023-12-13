package org.hyperledger.besu.ethereum.eth.sync.snapsync.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.util.Collections;

import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class StorageRangeDataRequestTest {
  private static final Hash HASH_LAST = Hash.fromHexString("F".repeat(64));

  @Mock WorldStateStorage storage;

  @Test
  public void assertEmptySlotsWithProofOfExclusionCompletes() {
    var worldstateProofProvider = new WorldStateProofProvider(storage);

    var storageRangeRequest =
        new StorageRangeDataRequest(
            Hash.EMPTY_TRIE_HASH, Bytes32.ZERO, Hash.EMPTY_TRIE_HASH, Bytes32.ZERO, HASH_LAST);

    var proofs = new ArrayDeque<Bytes>();
    proofs.add(0, Hash.EMPTY_TRIE_HASH);

    storageRangeRequest.addResponse(
        null, worldstateProofProvider, Collections.emptyNavigableMap(), proofs);
    assertThat(storageRangeRequest.isResponseReceived()).isTrue();
  }
}
