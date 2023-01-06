package org.hyperledger.besu.ethereum.eth.sync.snapsync.request;

import static org.hyperledger.besu.ethereum.worldstate.DataStorageFormat.BONSAI;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapWorldDownloadState;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.trie.TrieNodeDecoder;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.rlp.RLP;

public class FlatteningTask extends StorageTrieNodeDataRequest {

  public FlatteningTask(
      final Hash nodeHash,
      final Hash accountHash,
      final StateTrieAccountValue accountValue,
      final Hash rootHash,
      final Bytes location) {
    super(nodeHash, accountHash, accountValue, rootHash, location);
  }

  @Override
  protected int doPersist(
      final WorldStateStorage worldStateStorage,
      final WorldStateStorage.Updater updater,
      final SnapWorldDownloadState downloadState,
      final SnapSyncState snapSyncState) {
    if (getSyncMode(worldStateStorage) == BONSAI) {
      BonsaiWorldStateKeyValueStorage.Updater bonsaiUpdater =
          (BonsaiWorldStateKeyValueStorage.Updater) updater;
      if (getLocation().isEmpty()) {
        System.out.println("Contract " + accountHash + " mark as flattened");
        bonsaiUpdater.markFlattened(accountHash);
      }
      TrieNodeDecoder.decodeNodes(getLocation(), data).stream()
          .filter(node -> !node.isReferencedByHash())
          .forEach(
              node ->
                  node.getValue()
                      .ifPresent(
                          value ->
                              bonsaiUpdater.putStorageValueBySlotHash(
                                  accountHash,
                                  getSlotHash(node.getLocation().orElseThrow(), node.getPath()),
                                  Bytes32.leftPad(RLP.decodeValue(value)))));
    }
    return 1;
  }

  private Hash getSlotHash(final Bytes location, final Bytes path) {
    return Hash.wrap(Bytes32.wrap(CompactEncoding.pathToBytes(Bytes.concatenate(location, path))));
  }

  @Override
  public boolean isResponseReceived() {
    return true;
  }
}
