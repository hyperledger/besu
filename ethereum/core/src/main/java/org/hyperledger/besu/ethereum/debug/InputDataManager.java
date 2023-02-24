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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputDataManager {

  private static final Logger LOG = LoggerFactory.getLogger(InputDataManager.class);

  private final List<Bytes> inputDataCollection = Collections.synchronizedList(new ArrayList<>());

  public Bytes getInputData(final int index) {
    return inputDataCollection.get(index);
  }

  public int addInputData(final Bytes bytes) {
    int index = inputDataCollection.indexOf(bytes);
    LOG.trace("index found " + index);
    if (index >= 0) return index;
    this.inputDataCollection.add(bytes);
    // return the index the last element, just added
    return inputDataCollection.size() - 1;
  }
}
