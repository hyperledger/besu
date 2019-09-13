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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.filter;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.api.LogsQuery;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Log;
import tech.pegasys.pantheon.ethereum.core.LogTopic;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Test;

public class LogsQueryTest {

  @Test
  public void wildcardQueryAddressTopicReturnTrue() {
    final LogsQuery query = new LogsQuery.Builder().build();

    final Address address = Address.fromHexString("0x1111111111111111111111111111111111111111");
    final BytesValue data = BytesValue.fromHexString("0x0102");
    final List<LogTopic> topics = new ArrayList<>();
    final Log log = new Log(address, data, topics);

    assertThat(query.matches(log)).isTrue();
  }

  @Test
  public void univariateAddressMatchReturnsTrue() {
    final Address address = Address.fromHexString("0x1111111111111111111111111111111111111111");
    final LogsQuery query = new LogsQuery.Builder().address(address).build();

    final List<LogTopic> topics = new ArrayList<>();
    final Log log = new Log(address, BytesValue.fromHexString("0x0102"), topics);

    assertThat(query.matches(log)).isTrue();
  }

  @Test
  public void univariateAddressMismatchReturnsFalse() {
    final Address address1 = Address.fromHexString("0x1111111111111111111111111111111111111111");
    final LogsQuery query = new LogsQuery.Builder().address(address1).build();

    final Address address2 = Address.fromHexString("0x2222222222222222222222222222222222222222");
    final BytesValue data = BytesValue.fromHexString("0x0102");
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
    final Log log = new Log(address1, BytesValue.fromHexString("0x0102"), topics);

    assertThat(query.matches(log)).isTrue();
  }

  @Test
  public void multivariateAddressMismatchReturnsFalse() {
    final Address address1 = Address.fromHexString("0x1111111111111111111111111111111111111111");
    final Address address2 = Address.fromHexString("0x2222222222222222222222222222222222222222");
    final LogsQuery query = new LogsQuery.Builder().addresses(address1, address2).build();

    final Address address3 = Address.fromHexString("0x3333333333333333333333333333333333333333");
    final BytesValue data = BytesValue.fromHexString("0x0102");
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
    final Log log = new Log(address, BytesValue.fromHexString("0x0102"), Lists.newArrayList());

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
    final Log log = new Log(address, BytesValue.fromHexString("0x0102"), Lists.newArrayList(topic));

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

    final BytesValue data = BytesValue.fromHexString("0x0102");
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
    final Log log = new Log(address, BytesValue.fromHexString("0x0102"), logTopics);

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
    final Log log = new Log(address1, BytesValue.fromHexString("0x0102"), logTopics);

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
    final Log log = new Log(address, BytesValue.fromHexString("0x0102"), logTopics);

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
    final Log log = new Log(address, BytesValue.fromHexString("0x0102"), logTopics);

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
    final Log log = new Log(address, BytesValue.fromHexString("0x0102"), logTopics);

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
    final Log log = new Log(address, BytesValue.fromHexString("0x0102"), logTopics);

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
    final Log log = new Log(address, BytesValue.fromHexString("0x0102"), logTopics);

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
    final Log log = new Log(address, BytesValue.fromHexString("0x0102"), logTopics);

    assertThat(query.matches(log)).isTrue();
  }
}
