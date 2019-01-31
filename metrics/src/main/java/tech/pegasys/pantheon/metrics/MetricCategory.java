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
package tech.pegasys.pantheon.metrics;

public enum MetricCategory {
  BIG_QUEUE("big_queue"),
  BLOCKCHAIN("blockchain"),
  JVM("jvm", false),
  NETWORK("network"),
  PEERS("peers"),
  PROCESS("process", false),
  ROCKSDB("rocksdb"),
  RPC("rpc"),
  SYNCHRONIZER("synchronizer");

  private final String name;
  private final boolean pantheonSpecific;

  MetricCategory(final String name) {
    this(name, true);
  }

  MetricCategory(final String name, final boolean pantheonSpecific) {
    this.name = name;
    this.pantheonSpecific = pantheonSpecific;
  }

  public String getName() {
    return name;
  }

  public boolean isPantheonSpecific() {
    return pantheonSpecific;
  }
}
