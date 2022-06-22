/*
 * Copyright contributors to Hyperledger Besu
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.services.tasks.InMemoryTaskQueue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.rlp.RLP;

public class SnapContextLoader {

  private static final String SNAP_QUEUE_FILENAME = "queue.data";

  private static final String SNAP_ACCOUNT_FILENAME = "account.data";

  private final Optional<Path> maybeDirectory;

  private List<SnapDataRequest> snapDataRequests;

  private final List<Bytes> inconsistentAccounts;

  public SnapContextLoader(final Path directory) {
    this.maybeDirectory = Optional.of(directory);
    this.snapDataRequests = new ArrayList<>();
    this.inconsistentAccounts = new ArrayList<>();
  }

  public void loadContext() {
    loadTasks();
    loadInconsistentAccounts();
  }

  private void loadTasks() {
    maybeDirectory.ifPresent(
        path -> {
          final File savedQueue = maybeDirectory.get().resolve(SNAP_QUEUE_FILENAME).toFile();
          if (savedQueue.exists()) {
            try (final FileInputStream fis = new FileInputStream(savedQueue)) {
              final ObjectInputStream ois = new ObjectInputStream(fis);
              final BytesValueRLPInput rlpInput =
                  new BytesValueRLPInput(Bytes.wrap(ois.readAllBytes()), false);
              snapDataRequests = rlpInput.readList(SnapDataRequest::deserialize);
              ois.close();
            } catch (final Throwable e) {
              e.printStackTrace(System.out);
              System.out.println(e.getMessage());
              // ignore and restart from scratch
            }
          }
        });
  }

  private void loadInconsistentAccounts() {
    maybeDirectory.ifPresent(
        path -> {
          final File savedInconsistentAccounts =
              maybeDirectory.get().resolve(SNAP_ACCOUNT_FILENAME).toFile();
          if (savedInconsistentAccounts.exists()) {
            try (final FileInputStream fis = new FileInputStream(savedInconsistentAccounts)) {
              final ObjectInputStream ois = new ObjectInputStream(fis);
              while (ois.available() == Bytes32.SIZE) {
                inconsistentAccounts.add(Bytes32.wrap(ois.readNBytes(Bytes32.SIZE)));
              }
              ois.close();
            } catch (final Throwable e) {
              e.printStackTrace(System.out);
              System.out.println(e.getMessage());
              // ignore and restart from scratch
            }
          }
        });
  }

  public synchronized void updateContext(
      final InMemoryTaskQueue<SnapDataRequest> tasks, final List<Bytes> inconsistentAccounts) {
    System.out.println("want persist");
    saveTasks(tasks);
    saveInconsistentAccounts(inconsistentAccounts);
  }

  private void saveTasks(final InMemoryTaskQueue<SnapDataRequest> tasks) {
    maybeDirectory.ifPresent(
        path -> {
          try (final FileOutputStream fos =
              new FileOutputStream(path.resolve(SNAP_QUEUE_FILENAME).toFile(), false)) {
            final ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.write(
                RLP.encodeList(tasks.asList(), (rlpWriter, t) -> rlpWriter.writeRLP(t.serialize()))
                    .toArrayUnsafe());
            oos.close();
          } catch (Throwable e) {
            e.printStackTrace(System.out);
            System.out.println("write tasks issue " + e.getMessage());
          }
        });
  }

  private void saveInconsistentAccounts(final List<Bytes> inconsistentAccounts) {
    maybeDirectory.ifPresent(
        path -> {
          final File file = path.resolve(SNAP_ACCOUNT_FILENAME).toFile();
          final long length = file.length();
          try (final FileOutputStream fos = new FileOutputStream(file, true)) {
            final ObjectOutputStream oos = new ObjectOutputStream(fos);
            int index = (int) Math.max(Bytes32.SIZE, length) / Bytes32.SIZE;
            for (int i = index; i < inconsistentAccounts.size(); i++) {
              oos.write(inconsistentAccounts.get(i - 1).toArrayUnsafe());
            }
            oos.close();
          } catch (Throwable e) {
            e.printStackTrace(System.out);
            System.out.println("write accounts issue " + e.getMessage());
          }
        });
  }

  public void clear() {
    maybeDirectory.ifPresent(
        path -> {
          path.resolve(SNAP_ACCOUNT_FILENAME).toFile().delete();
          path.resolve(SNAP_QUEUE_FILENAME).toFile().delete();
        });
  }

  public boolean isContextAvailable() {
    return !snapDataRequests.isEmpty() || !inconsistentAccounts.isEmpty();
  }

  public boolean isHealing() {
    return snapDataRequests.isEmpty() && !inconsistentAccounts.isEmpty();
  }

  public List<SnapDataRequest> getSnapDataRequests() {
    return snapDataRequests;
  }

  public List<Bytes> getInconsistentAccounts() {
    return inconsistentAccounts;
  }
}
