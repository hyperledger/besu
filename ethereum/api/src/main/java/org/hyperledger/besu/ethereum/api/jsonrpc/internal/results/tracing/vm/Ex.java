/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.vm;

import java.util.ArrayList;
import java.util.List;

public class Ex {
  private Mem mem;
  private List<String> push;
  private Store store;
  private long used;

  public Ex() {
    push = new ArrayList<>();
  }

  public Ex(final Mem mem, final List<String> push, final Store store, final long used) {
    this.mem = mem;
    this.push = push;
    this.store = store;
    this.used = used;
  }

  public Mem getMem() {
    return mem;
  }

  public List<String> getPush() {
    return push;
  }

  public void addPush(final String value) {
    push.add(value);
  }

  public Store getStore() {
    return store;
  }

  public long getUsed() {
    return used;
  }

  public void setMem(final Mem mem) {
    this.mem = mem;
  }

  public void setStore(final Store store) {
    this.store = store;
  }

  public void setUsed(final long used) {
    this.used = used;
  }
}
