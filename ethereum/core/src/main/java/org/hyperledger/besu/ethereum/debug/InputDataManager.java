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
package org.hyperledger.besu.ethereum.debug;

import org.hyperledger.besu.datatypes.Hash;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tuweni.bytes.Bytes;

public class InputDataManager {

  private final Map<Hash, Bytes> inputDataCollection = new ConcurrentHashMap<>();

  public Bytes getInputData(final Hash hash) {
    return inputDataCollection.get(hash);
  }

  public void addInputData(final Hash hash, final Bytes bytes) {
    if (!inputDataCollection.containsKey(hash)) inputDataCollection.put(hash, bytes);
  }
}
