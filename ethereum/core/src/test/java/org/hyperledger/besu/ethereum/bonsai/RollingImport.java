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
 *
 */

package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.util.io.RollingFileReader;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.tuweni.bytes.Bytes;

public class RollingImport {

  public static void main(final String[] arg) throws IOException {
    final RollingFileReader reader =
        new RollingFileReader(
            (i, c) -> Path.of(String.format("/tmp/goerli/fill/besu-layer-%04d.rdat", i)), false);

    final BonsaiPersistdWorldState bonsaiState =
        new BonsaiPersistdWorldState(
            new InMemoryKeyValueStorage(),
            new InMemoryKeyValueStorage(),
            new InMemoryKeyValueStorage(),
            new InMemoryKeyValueStorage(),
            new InMemoryKeyValueStorage());

    int count = 0;
    while (!reader.isDone()) {
      try {
        final byte[] bytes = reader.readBytes();
        if (bytes.length < 1) {
          continue;
        }
        final TrieLogLayer layer =
            TrieLogLayer.readFrom(new BytesValueRLPInput(Bytes.wrap(bytes), false));
        bonsaiState.rollForward(layer);
        if (count % 10000 == 0) {
          System.out.println(". - " + count);
        } else if (count % 100 == 0) {
          System.out.print(".");
          System.out.flush();
        }
      } catch (final Exception e) {
//        e.printStackTrace(System.out);
        System.out.println(count);
        throw e;
      }
      count++;
    }
  }
}
