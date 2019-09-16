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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A series of {@link Log} entries accrued during the execution of a transaction.
 *
 * <p>Note that this class is essentially a list of {@link Log} with maintain a bloom filter of
 * those logs. Note however that while it is a mutable list, only additions are allowed: trying to
 * set or remove an element will throw {@link UnsupportedOperationException} (as we cannot update
 * the bloom filter properly on such operations).
 */
public class LogSeries extends AbstractList<Log> {

  public static LogSeries empty() {
    return new LogSeries(new ArrayList<>());
  }

  private final List<Log> series;
  private final LogsBloomFilter bloomFilter;

  public LogSeries(final Collection<Log> logs) {
    this.series = new ArrayList<>(logs);
    this.bloomFilter = new LogsBloomFilter();

    logs.forEach(bloomFilter::insertLog);
  }

  /**
   * The bloom filter composed of the content of this log series.
   *
   * @return the log series bloom filter.
   */
  public LogsBloomFilter getBloomFilter() {
    return bloomFilter;
  }

  /**
   * Writes the log series to the provided RLP output.
   *
   * @param out the output in which to encode the log series.
   */
  public void writeTo(final RLPOutput out) {
    out.writeList(series, Log::writeTo);
  }

  /**
   * Reads the log series from the provided RLP input.
   *
   * @param in the input from which to decode the log series.
   * @return the read logs series.
   */
  public static LogSeries readFrom(final RLPInput in) {
    final List<Log> logs = in.readList(Log::readFrom);
    return new LogSeries(logs);
  }

  @Override
  public Log get(final int i) {
    return series.get(i);
  }

  @Override
  public int size() {
    return series.size();
  }

  @Override
  public void add(final int index, final Log log) {
    series.add(index, log);
    bloomFilter.insertLog(log);
  }

  @Override
  public void clear() {
    series.clear();
  }
}
