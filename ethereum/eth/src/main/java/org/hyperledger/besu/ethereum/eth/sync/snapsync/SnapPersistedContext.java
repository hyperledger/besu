package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.AccountRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.services.tasks.FlatFileTaskCollection;
import org.hyperledger.besu.services.tasks.Task;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;

public class SnapPersistedContext {

  private final FlatFileTaskCollection<AccountRangeDataRequest> accountRangeToDownload;

  private final FlatFileTaskCollection<Bytes> inconsistentAccounts;

  public SnapPersistedContext(final Path dataDir) {
    this.accountRangeToDownload =
        new FlatFileTaskCollection<>(
            dataDir,
            AccountRangeDataRequest::serialize,
            bytes -> AccountRangeDataRequest.deserialize(new BytesValueRLPInput(bytes, false)),
            "tasks");
    this.inconsistentAccounts =
        new FlatFileTaskCollection<>(dataDir, Function.identity(), Function.identity(), "accounts");
  }

  public void updatePersistedTasks(final List<? extends SnapDataRequest> accountRangeDataRequests) {
    accountRangeToDownload.clear();
    accountRangeDataRequests.stream()
        .map(AccountRangeDataRequest.class::cast)
        .forEach(accountRangeToDownload::add);
  }

  public void addInconsistentAccount(final Bytes inconsistentAccount) {
    inconsistentAccounts.add(inconsistentAccount);
  }

  public List<AccountRangeDataRequest> getPersistedTasks() {
    return accountRangeToDownload.getAll().stream().map(Task::getData).collect(Collectors.toList());
  }

  public HashSet<Bytes> getInconsistentAccounts() {
    return inconsistentAccounts.getAll().stream()
        .map(Task::getData)
        .collect(Collectors.toCollection(HashSet::new));
  }

  public boolean isContextAvailable() {
    return !accountRangeToDownload.isEmpty() || !inconsistentAccounts.isEmpty();
  }

  public boolean isHealing() {
    return accountRangeToDownload.isEmpty() && !inconsistentAccounts.isEmpty();
  }

  public void clearPersistedTasks() {
    accountRangeToDownload.clear();
  }

  public void clear() {
    accountRangeToDownload.clear();
    inconsistentAccounts.clear();
  }
}
