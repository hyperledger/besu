/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.worldstate;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.MutableAccount;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.core.WorldState;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.WorldStatePreimageKeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A simple extension of {@link DefaultMutableWorldState} that tracks in memory the mapping of hash
 * to address for its accounts for debugging purposes. It also provides a full toString() method
 * that display the content of the world state. It is obviously only mean for testing or debugging.
 */
public class DebuggableMutableWorldState extends DefaultMutableWorldState {

  // TODO: This is more complex than it should due to DefaultMutableWorldState.accounts() not being
  // implmemented (pending NC-746). Once that is fixed, we won't need to keep the set of account
  // hashes at all, just the hashtoAddress map (this is also why things are separated this way,
  // it will make it easier to update later).

  private static class DebugInfo {
    private final Set<Address> accounts = new HashSet<>();

    private void addAll(final DebugInfo other) {
      this.accounts.addAll(other.accounts);
    }
  }

  private final DebugInfo info = new DebugInfo();

  public DebuggableMutableWorldState() {
    super(
        new WorldStateKeyValueStorage(new InMemoryKeyValueStorage()),
        new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage()));
  }

  public DebuggableMutableWorldState(final WorldState worldState) {
    super(worldState);

    if (worldState instanceof DebuggableMutableWorldState) {
      final DebuggableMutableWorldState dws = ((DebuggableMutableWorldState) worldState);
      info.addAll(dws.info);
    } else {
      // TODO: on NC-746 gets in, we can remove this. That is, post NC-746, we won't be relying
      // on info.accounts to know that accounts exists, so the only thing we will not have in
      // this branch is info.addressToHash, but that's not a huge deal.
      throw new RuntimeException(worldState + " is not a debuggable word state");
    }
  }

  @Override
  public WorldUpdater updater() {
    return new InfoCollectingUpdater(super.updater(), info);
  }

  @Override
  public Stream<Account> streamAccounts() {
    return info.accounts.stream().map(this::get).filter(Objects::nonNull);
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(rootHash()).append(":\n");
    streamAccounts()
        .forEach(
            account -> {
              final Address address = account.getAddress();
              builder
                  .append("  ")
                  .append(address == null ? "<unknown>" : address)
                  .append(" [")
                  .append(account.getAddressHash())
                  .append("]:\n");
              builder.append("    nonce: ").append(account.getNonce()).append('\n');
              builder.append("    balance: ").append(account.getBalance()).append('\n');
              builder.append("    code: ").append(account.getCode()).append('\n');
            });
    return builder.toString();
  }

  private class InfoCollectingUpdater implements WorldUpdater {
    private final WorldUpdater wrapped;
    private final DebugInfo commitInfo;
    private DebugInfo ownInfo = new DebugInfo();

    InfoCollectingUpdater(final WorldUpdater wrapped, final DebugInfo info) {
      this.wrapped = wrapped;
      this.commitInfo = info;
    }

    private void record(final Address address) {
      ownInfo.accounts.add(address);
    }

    @Override
    public MutableAccount createAccount(
        final Address address, final long nonce, final Wei balance) {
      record(address);
      return wrapped.createAccount(address, nonce, balance);
    }

    @Override
    public MutableAccount createAccount(final Address address) {
      record(address);
      return wrapped.createAccount(address);
    }

    @Override
    public MutableAccount getOrCreate(final Address address) {
      record(address);
      return wrapped.getOrCreate(address);
    }

    @Override
    public MutableAccount getMutable(final Address address) {
      record(address);
      return wrapped.getMutable(address);
    }

    @Override
    public void deleteAccount(final Address address) {
      wrapped.deleteAccount(address);
    }

    @Override
    public Collection<Account> getTouchedAccounts() {
      return wrapped.getTouchedAccounts();
    }

    @Override
    public void revert() {
      ownInfo = new DebugInfo();
      wrapped.revert();
    }

    @Override
    public void commit() {
      commitInfo.addAll(ownInfo);
      wrapped.commit();
    }

    @Override
    public WorldUpdater updater() {
      return new InfoCollectingUpdater(wrapped.updater(), ownInfo);
    }

    @Override
    public Account get(final Address address) {
      record(address);
      return wrapped.get(address);
    }
  }
}
