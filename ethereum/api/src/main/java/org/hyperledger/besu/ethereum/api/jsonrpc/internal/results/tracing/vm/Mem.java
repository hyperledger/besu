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

public class Mem {

  private String data;
  private int off;

  public Mem(final String data) {
    this(data, 0);
  }

  public Mem(final String data, final int off) {
    this.data = data;
    this.off = off;
  }

  public String getData() {
    return data;
  }

  public int getOff() {
    return off;
  }
}
