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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;
import java.util.Objects;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

/**
 * A log entry is a tuple of a logger’s address (the address of the contract that added the logs), a
 * series of 32-bytes log topics, and some number of bytes of data.
 */
public class Log implements org.hyperledger.besu.plugin.data.Log {

  private final Address logger;
  private final BytesValue data;
  private final ImmutableList<LogTopic> topics;

  /**
   * @param logger The address of the contract that produced this log.
   * @param data Data associated with this log.
   * @param topics Indexable topics associated with this log.
   */
  public Log(final Address logger, final BytesValue data, final List<LogTopic> topics) {
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
    out.writeBytesValue(logger);
    out.writeList(topics, LogTopic::writeTo);
    out.writeBytesValue(data);
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
    final Address logger = Address.wrap(in.readBytesValue());
    final List<LogTopic> topics = in.readList(LogTopic::readFrom);
    final BytesValue data = in.readBytesValue();
    in.leaveList();
    return new Log(logger, data, topics);
  }

  @Override
  public Address getLogger() {
    return logger;
  }

  @Override
  public BytesValue getData() {
    return data;
  }

  @Override
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
