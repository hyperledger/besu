package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.TrieNodeDataRequest;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.pipeline.Pipe;
import org.hyperledger.besu.services.tasks.Task;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;

public class FlatteningStep {
  private final WorldStateStorage worldStateStorage;
  private final SnapWorldDownloadState downloadState;
  private final SnapSyncState snapSyncState;

  public FlatteningStep(
      final WorldStateStorage worldStateStorage,
      final SnapWorldDownloadState downloadState,
      final SnapSyncState snapSyncState) {
    this.worldStateStorage = worldStateStorage;
    this.downloadState = downloadState;
    this.snapSyncState = snapSyncState;
  }

  public Stream<Task<SnapDataRequest>> flatData(
      final Task<SnapDataRequest> task, final Pipe<Task<SnapDataRequest>> completedTasks) {
    final TrieNodeDataRequest request = (TrieNodeDataRequest) task.getData();
    // check if node is already stored in the worldstate
    Optional<Bytes> existingData = request.getExistingData(worldStateStorage);
    if (existingData.isPresent()) {
      request.setData(existingData.get());
      downloadState.enqueueRequests(
          request.getChildRequests(downloadState, worldStateStorage, snapSyncState));
      final WorldStateStorage.Updater updater = worldStateStorage.updater();
      request.persist(worldStateStorage, updater, downloadState, snapSyncState);
      updater.commit();
    }
    return Stream.of(task);
  }
}
