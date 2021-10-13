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
package org.hyperledger.besu.evm.log;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.List;
import java.util.Objects;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.apache.tuweni.bytes.Bytes;

/**
 * A log entry is a tuple of a logger’s address (the address of the contract that added the logs), a
 * series of 32-bytes log topics, and some number of bytes of data.
 */
public class Log {

  private final Address logger;
  private final Bytes data;
  private final ImmutableList<LogTopic> topics;

  /**
   * @param logger The address of the contract that produced this log.
   * @param data Data associated with this log.
   * @param topics Indexable topics associated with this log.
   */
  public Log(final Address logger, final Bytes data, final List<LogTopic> topics) {
    this.logger = logger;
    this.data = data;
    this.topics = ImmutableList.copyOf(topics);
  }

  /**
   * Writes the log entry to the provided RLP output.
   *
   * @param out the output in which to encode the log entry.
   */
  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeBytes(logger);
    out.writeList(topics, (topic, listOut) -> listOut.writeBytes(topic));
    out.writeBytes(data);
    out.endList();
  }

  /**
   * Reads the log entry from the provided RLP input.
   *
   * @param in the input from which to decode the log entry.
   * @return the read log entry.
   */
  public static Log readFrom(final RLPInput in) {
    in.enterList();
    final Address logger = Address.wrap(in.readBytes());
    final List<LogTopic> topics = in.readList(listIn -> LogTopic.wrap(listIn.readBytes32()));
    final Bytes data = in.readBytes();
    in.leaveList();
    return new Log(logger, data, topics);
  }

  public Address getLogger() {
    return logger;
  }

  public Bytes getData() {
    return data;
  }

  public List<LogTopic> getTopics() {
    return topics;
  }

  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof Log)) return false;

    // Compare data
    final Log that = (Log) other;
    return this.data.equals(that.data)
        && this.logger.equals(that.logger)
        && this.topics.equals(that.topics);
  }

  @Override
  public int hashCode() {
    return Objects.hash(data, logger, topics);
  }

  @Override
  public String toString() {
    final String joinedTopics = Joiner.on("\n").join(topics);
    return String.format("Data: %s\nLogger: %s\nTopics: %s", data, logger, joinedTopics);
  }
}
