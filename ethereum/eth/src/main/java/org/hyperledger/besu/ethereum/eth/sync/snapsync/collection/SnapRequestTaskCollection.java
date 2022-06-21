/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.sync.snapsync.collection;

import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.services.tasks.InMemoryTaskQueue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.rlp.RLP;

public class SnapRequestTaskCollection extends InMemoryTaskQueue<SnapDataRequest> {

  private static final String SNAP_QUEUE_FILENAME = "queue.data";

  private final Optional<Path> maybeDirectory;

  public SnapRequestTaskCollection(final Path directory) {
    this.maybeDirectory = Optional.of(directory);
  }

  public SnapRequestTaskCollection() {
    this.maybeDirectory = Optional.empty();
  }

  public boolean loadPersistQueue(final Consumer<SnapDataRequest> onRequestFound) {
    if (maybeDirectory.isPresent()) {
      final File savedQueue = maybeDirectory.get().resolve(SNAP_QUEUE_FILENAME).toFile();
      if (savedQueue.exists()) {
        try (final FileInputStream fis = new FileInputStream(savedQueue)) {
          final ObjectInputStream ois = new ObjectInputStream(fis);
          final RLPInput rlpInput = new BytesValueRLPInput(Bytes.wrap(ois.readAllBytes()), false);
          rlpInput
              .readList(SnapDataRequest::deserialize)
              .forEach(
                  snapDataRequest -> {
                    add(snapDataRequest);
                    onRequestFound.accept(snapDataRequest);
                  });
          ois.close();
        } catch (final Throwable e) {
          // ignore and restart from scratch
        }
      }
    }
    return isEmpty();
  }

  public synchronized void persist() {
    System.out.println("want persist");
    maybeDirectory.ifPresent(
        path -> {
          try (final FileOutputStream fos =
              new FileOutputStream(path.resolve(SNAP_QUEUE_FILENAME).toFile(), false)) {
            final ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.write(
                RLP.encodeList(asList(), (rlpWriter, t) -> rlpWriter.writeRLP(t.serialize()))
                    .toArrayUnsafe());
            oos.close();
          } catch (Throwable e) {
            e.printStackTrace(System.out);
            System.out.println("write issue " + e.getMessage());
          }
        });
  }
}
