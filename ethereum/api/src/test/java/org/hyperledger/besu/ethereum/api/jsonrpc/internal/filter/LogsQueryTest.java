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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.query.LogsQuery;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class LogsQueryTest {

  private static final Bytes data = Bytes.fromHexString("0x0102");

  @Test
  public void wildcardQueryAddressTopicReturnTrue() {
    final LogsQuery query = new LogsQuery.Builder().build();

    final Address address = Address.fromHexString("0x1111111111111111111111111111111111111111");
    final List<LogTopic> topics = new ArrayList<>();
    final Log log = new Log(address, data, topics);

    assertThat(query.couldMatch(LogsBloomFilter.builder().insertLog(log).build())).isTrue();
    assertThat(query.matches(log)).isTrue();
  }

  @Test
  public void univariateAddressMatchReturnsTrue() {
    final Address address = Address.fromHexString("0x1111111111111111111111111111111111111111");
    final LogsQuery query = new LogsQuery.Builder().address(address).build();

    final List<LogTopic> topics = new ArrayList<>();
    final Log log = new Log(address, data, topics);

    assertThat(query.couldMatch(LogsBloomFilter.builder().insertLog(log).build())).isTrue();
    assertThat(query.matches(log)).isTrue();
  }

  @Test
  public void univariateAddressMismatchReturnsFalse() {
    final Address address1 = Address.fromHexString("0x1111111111111111111111111111111111111111");
    final LogsQuery query = new LogsQuery.Builder().address(address1).build();

    final Address address2 = Address.fromHexString("0x2222222222222222222222222222222222222222");
    final List<LogTopic> topics = new ArrayList<>();
    final Log log = new Log(address2, data, topics);

    assertThat(query.matches(log)).isFalse();
  }

  @Test
  public void multivariateAddressQueryMatchReturnsTrue() {
    final Address address1 = Address.fromHexString("0x1111111111111111111111111111111111111111");
    final Address address2 = Address.fromHexString("0x2222222222222222222222222222222222222222");
    final LogsQuery query = new LogsQuery.Builder().addresses(address1, address2).build();

    final List<LogTopic> topics = new ArrayList<>();
    final Log log = new Log(address1, data, topics);

    assertThat(query.couldMatch(LogsBloomFilter.builder().insertLog(log).build())).isTrue();
    assertThat(query.matches(log)).isTrue();
  }

  @Test
  public void multivariateAddressMismatchReturnsFalse() {
    final Address address1 = Address.fromHexString("0x1111111111111111111111111111111111111111");
    final Address address2 = Address.fromHexString("0x2222222222222222222222222222222222222222");
    final LogsQuery query = new LogsQuery.Builder().addresses(address1, address2).build();

    final Address address3 = Address.fromHexString("0x3333333333333333333333333333333333333333");
    final List<LogTopic> topics = new ArrayList<>();
    final Log log = new Log(address3, data, topics);

    assertThat(query.matches(log)).isFalse();
  }

  @Test
  public void univariateTopicQueryLogWithoutTopicReturnFalse() {
    final Address address = Address.fromHexString("0x1111111111111111111111111111111111111111");
    final LogTopic topic =
        LogTopic.fromHexString(
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    final List<LogTopic> topics = new ArrayList<>();
    topics.add(topic);

    final List<List<LogTopic>> topicsQuery = new ArrayList<>();
    topicsQuery.add(topics);

    final LogsQuery query = new LogsQuery.Builder().address(address).topics(topicsQuery).build();
    final Log log = new Log(address, data, Lists.newArrayList());

    assertThat(query.matches(log)).isFalse();
  }

  @Test
  public void univariateTopicQueryMatchReturnTrue() {
    final Address address = Address.fromHexString("0x1111111111111111111111111111111111111111");
    final LogTopic topic =
        LogTopic.fromHexString(
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    final List<LogTopic> topics = new ArrayList<>();
    topics.add(topic);

    final List<List<LogTopic>> topicsQuery = new ArrayList<>();
    topicsQuery.add(topics);

    final LogsQuery query = new LogsQuery.Builder().address(address).topics(topicsQuery).build();
    final Log log = new Log(address, data, Lists.newArrayList(topic));

    assertThat(query.couldMatch(LogsBloomFilter.builder().insertLog(log).build())).isTrue();
    assertThat(query.matches(log)).isTrue();
  }

  @Test
  public void univariateTopicQueryMismatchReturnFalse() {
    final Address address = Address.fromHexString("0x1111111111111111111111111111111111111111");
    final LogTopic topic =
        LogTopic.fromHexString(
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    final List<LogTopic> topics = Lists.newArrayList(topic);
    final List<List<LogTopic>> topicsQuery = new ArrayList<>();
    topicsQuery.add(topics);
    final LogsQuery query = new LogsQuery.Builder().address(address).topics(topicsQuery).build();

    topics.add(
        LogTopic.fromHexString(
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
    final Log log = new Log(address, data, Lists.newArrayList());

    assertThat(query.matches(log)).isFalse();
  }

  @Test
  public void multivariateTopicQueryMismatchReturnFalse() {
    final Address address = Address.fromHexString("0x1111111111111111111111111111111111111111");

    final LogTopic topic1 =
        LogTopic.fromHexString(
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    final LogTopic topic2 =
        LogTopic.fromHexString(
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    final LogTopic topic3 =
        LogTopic.fromHexString(
            "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");

    final List<LogTopic> logTopics = new ArrayList<>();
    logTopics.add(topic1);
    logTopics.add(topic2);

    final List<LogTopic> queryTopics = new ArrayList<>();
    queryTopics.add(topic3);

    final List<List<LogTopic>> queryParameter = new ArrayList<>();
    queryParameter.add(queryTopics);

    final LogsQuery query = new LogsQuery.Builder().address(address).topics(queryParameter).build();
    final Log log = new Log(address, data, logTopics);

    assertThat(query.matches(log)).isFalse();
  }

  /** [null, B] "anything in first position AND B in second position (and anything after)" */
  @Test
  public void multivariateSurplusTopicMatchMultivariateNullQueryReturnTrue() {
    final Address address1 = Address.fromHexString("0x1111111111111111111111111111111111111111");

    final LogTopic topic1 =
        LogTopic.fromHexString(
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    final LogTopic topic2 =
        LogTopic.fromHexString(
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    final LogTopic topic3 =
        LogTopic.fromHexString(
            "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
    final LogTopic topic4 = null;

    final List<LogTopic> logTopics = new ArrayList<>();
    logTopics.add(topic1);
    logTopics.add(topic2);
    logTopics.add(topic3);

    final List<LogTopic> queryTopics = new ArrayList<>();
    queryTopics.add(topic4);
    queryTopics.add(topic2);

    final List<List<LogTopic>> queryParameter = new ArrayList<>();
    queryParameter.add(queryTopics);

    final LogsQuery query =
        new LogsQuery.Builder().address(address1).topics(queryParameter).build();
    final Log log = new Log(address1, data, logTopics);

    assertThat(query.couldMatch(LogsBloomFilter.builder().insertLog(log).build())).isTrue();
    assertThat(query.matches(log)).isTrue();
  }

  /** [A, B] "A in first position AND B in second position (and anything after)" */
  @Test
  public void multivariateSurplusTopicMatchMultivariateQueryReturnTrue_00() {
    final Address address = Address.fromHexString("0x1111111111111111111111111111111111111111");

    final LogTopic topic1 =
        LogTopic.fromHexString(
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    final LogTopic topic2 =
        LogTopic.fromHexString(
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

    final List<LogTopic> logTopics = new ArrayList<>();
    logTopics.add(topic1);
    logTopics.add(topic2);

    final List<LogTopic> queryTopics = new ArrayList<>();
    queryTopics.add(topic1);
    queryTopics.add(topic2);

    final List<List<LogTopic>> queryParameter = new ArrayList<>();
    queryParameter.add(queryTopics);

    final LogsQuery query = new LogsQuery.Builder().address(address).topics(queryParameter).build();
    final Log log = new Log(address, data, logTopics);

    assertThat(query.couldMatch(LogsBloomFilter.builder().insertLog(log).build())).isTrue();
    assertThat(query.matches(log)).isTrue();
  }

  /** [A, B] "A in first position AND B in second position (and anything after)" */
  @Test
  public void multivariateSurplusTopicMatchMultivariateQueryReturnTrue_01() {
    final Address address = Address.fromHexString("0x1111111111111111111111111111111111111111");

    final LogTopic topic1 =
        LogTopic.fromHexString(
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    final LogTopic topic2 =
        LogTopic.fromHexString(
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    final LogTopic topic3 =
        LogTopic.fromHexString(
            "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");

    final List<LogTopic> logTopics = new ArrayList<>();
    logTopics.add(topic1);
    logTopics.add(topic2);

    final List<LogTopic> queryTopics = new ArrayList<>();
    queryTopics.add(topic1);
    queryTopics.add(topic3);

    final List<List<LogTopic>> queryParameter = new ArrayList<>();
    queryParameter.add(queryTopics);

    final LogsQuery query = new LogsQuery.Builder().address(address).topics(queryParameter).build();
    final Log log = new Log(address, data, logTopics);

    assertThat(query.couldMatch(LogsBloomFilter.builder().insertLog(log).build())).isTrue();
    assertThat(query.matches(log)).isTrue();
  }

  /** [A, B] "A in first position AND B in second position (and anything after)" */
  @Test
  public void multivariateSurplusTopicMatchMultivariateQueryReturnTrue_02() {
    final Address address = Address.fromHexString("0x1111111111111111111111111111111111111111");

    final LogTopic topic1 =
        LogTopic.fromHexString(
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    final LogTopic topic2 =
        LogTopic.fromHexString(
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    final LogTopic topic3 =
        LogTopic.fromHexString(
            "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");

    final List<LogTopic> logTopics = new ArrayList<>();
    logTopics.add(topic1);
    logTopics.add(topic2);
    logTopics.add(topic3);

    final List<LogTopic> queryTopics = new ArrayList<>();
    queryTopics.add(topic1);
    queryTopics.add(topic2);

    final List<List<LogTopic>> queryParameter = new ArrayList<>();
    queryParameter.add(queryTopics);

    final LogsQuery query = new LogsQuery.Builder().address(address).topics(queryParameter).build();
    final Log log = new Log(address, data, logTopics);

    assertThat(query.couldMatch(LogsBloomFilter.builder().insertLog(log).build())).isTrue();
    assertThat(query.matches(log)).isTrue();
  }

  /**
   * [[A, B], [A, B]] "(A OR B) in first position AND (A OR B) in second position (and anything
   * after)"
   */
  @Test
  public void redundantUnivariateTopicMatchMultivariateQueryReturnTrue() {
    final Address address = Address.fromHexString("0x1111111111111111111111111111111111111111");

    final LogTopic topic1 =
        LogTopic.fromHexString(
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    final LogTopic topic2 =
        LogTopic.fromHexString(
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

    final List<LogTopic> logTopics = new ArrayList<>();
    logTopics.add(topic2);
    logTopics.add(topic2);

    final List<LogTopic> queryTopics1 = new ArrayList<>();
    queryTopics1.add(topic1);
    queryTopics1.add(topic2);
    final List<LogTopic> queryTopics2 = new ArrayList<>();
    queryTopics2.add(topic1);
    queryTopics2.add(topic2);

    final List<List<LogTopic>> queryParameter = new ArrayList<>();
    queryParameter.add(queryTopics1);
    queryParameter.add(queryTopics2);

    final LogsQuery query = new LogsQuery.Builder().address(address).topics(queryParameter).build();
    final Log log = new Log(address, data, logTopics);

    assertThat(query.couldMatch(LogsBloomFilter.builder().insertLog(log).build())).isTrue();
    assertThat(query.matches(log)).isTrue();
  }

  @Test
  public void multivariateTopicMatchRedundantMultivariateQueryReturnTrue() {
    final Address address = Address.fromHexString("0x1111111111111111111111111111111111111111");

    final LogTopic topic1 =
        LogTopic.fromHexString(
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    final LogTopic topic2 =
        LogTopic.fromHexString(
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    final LogTopic topic3 =
        LogTopic.fromHexString(
            "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");

    final List<LogTopic> logTopics = new ArrayList<>();
    logTopics.add(topic1);
    logTopics.add(topic3);

    final List<LogTopic> queryTopics1 = new ArrayList<>();
    queryTopics1.add(topic1);
    queryTopics1.add(topic2);
    final List<LogTopic> queryTopics2 = new ArrayList<>();
    queryTopics2.add(topic1);
    queryTopics2.add(topic2);

    final List<List<LogTopic>> queryParameter = new ArrayList<>();
    queryParameter.add(queryTopics1);
    queryParameter.add(queryTopics2);

    final LogsQuery query = new LogsQuery.Builder().address(address).topics(queryParameter).build();
    final Log log = new Log(address, data, logTopics);

    assertThat(query.matches(log)).isFalse();
  }

  @Test
  public void multivariateTopicMatchMultivariateQueryReturnTrue() {
    final Address address = Address.fromHexString("0x1111111111111111111111111111111111111111");

    final LogTopic topic1 =
        LogTopic.fromHexString(
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    final LogTopic topic2 =
        LogTopic.fromHexString(
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    final LogTopic topic3 =
        LogTopic.fromHexString(
            "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
    final LogTopic topic4 =
        LogTopic.fromHexString(
            "0xdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd");

    final List<LogTopic> logTopics = new ArrayList<>();
    logTopics.add(topic1);
    logTopics.add(topic3);

    final List<LogTopic> queryTopics1 = new ArrayList<>();
    queryTopics1.add(topic1);
    queryTopics1.add(topic2);
    final List<LogTopic> queryTopics2 = new ArrayList<>();
    queryTopics2.add(topic3);
    queryTopics2.add(topic4);

    final List<List<LogTopic>> queryParameter = new ArrayList<>();
    queryParameter.add(queryTopics1);
    queryParameter.add(queryTopics2);

    final LogsQuery query = new LogsQuery.Builder().address(address).topics(queryParameter).build();
    final Log log = new Log(address, data, logTopics);

    assertThat(query.couldMatch(LogsBloomFilter.builder().insertLog(log).build())).isTrue();
    assertThat(query.matches(log)).isTrue();
  }

  @Test
  public void emptySubTopicProducesNoMatches() {
    final Address address = Address.fromHexString("0x1111111111111111111111111111111111111111");

    final LogTopic topic1 =
        LogTopic.fromHexString(
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    final LogTopic topic2 =
        LogTopic.fromHexString(
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    final List<List<LogTopic>> queryParameter =
        Lists.newArrayList(singletonList(topic1), emptyList());

    final LogsQuery query = new LogsQuery.Builder().address(address).topics(queryParameter).build();
    final Log log = new Log(address, data, Lists.newArrayList(topic1, topic2));

    assertThat(query.matches(log)).isFalse();
  }
}
