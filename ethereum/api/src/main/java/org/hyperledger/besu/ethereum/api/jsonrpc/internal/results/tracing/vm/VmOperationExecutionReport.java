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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.vm;

import java.util.ArrayList;
import java.util.List;

/** Record of an executed virtual machine operation. */
public class VmOperationExecutionReport {
  private Mem mem;
  private final List<String> push;
  private Store store;
  private long used;

  /** Instantiates a new VM operation execution report. */
  public VmOperationExecutionReport() {
    push = new ArrayList<>();
  }

  /**
   * Gets mem.
   *
   * @return the mem
   */
  public Mem getMem() {
    return mem;
  }

  /**
   * Gets push.
   *
   * @return the push
   */
  public List<String> getPush() {
    return push;
  }

  /**
   * Add push.
   *
   * @param value the value
   */
  public void addPush(final String value) {
    push.add(0, value);
  }

  /**
   * Single push.
   *
   * @param value the value
   */
  public void singlePush(final String value) {
    push.clear();
    push.add(value);
  }

  /**
   * Gets store.
   *
   * @return the store
   */
  public Store getStore() {
    return store;
  }

  /**
   * Gets used.
   *
   * @return the used
   */
  public long getUsed() {
    return used;
  }

  /**
   * Sets mem.
   *
   * @param mem the mem
   */
  public void setMem(final Mem mem) {
    this.mem = mem;
  }

  /**
   * Sets store.
   *
   * @param store the store
   */
  public void setStore(final Store store) {
    this.store = store;
  }

  /**
   * Sets used.
   *
   * @param used the used
   */
  public void setUsed(final long used) {
    this.used = used;
  }
}
