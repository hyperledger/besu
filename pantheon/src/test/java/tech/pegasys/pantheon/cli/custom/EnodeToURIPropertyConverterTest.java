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
package tech.pegasys.pantheon.cli.custom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.net.URI;

import org.junit.Test;

public class EnodeToURIPropertyConverterTest {

  private final String VALID_NODE_ID =
      "6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0";
  private final String IPV4_ADDRESS = "192.168.0.1";
  private final String IPV6_FULL_ADDRESS = "[2001:db8:85a3:0:0:8a2e:0370:7334]";
  private final String IPV6_COMPACT_ADDRESS = "[2001:db8:85a3::8a2e:0370:7334]";
  private final int P2P_PORT = 30303;
  private final String DISCOVERY_QUERY = "discport=30301";

  private final EnodeToURIPropertyConverter converter = new EnodeToURIPropertyConverter();

  @Test
  public void convertEnodeURLWithDiscoveryPortShouldBuildExpectedURI() {
    final String value =
        "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":" + P2P_PORT + "?" + DISCOVERY_QUERY;
    final URI expectedURI = URI.create(value);

    final URI convertedURI = converter.convert(value);

    assertThat(convertedURI).isEqualTo(expectedURI);
    assertThat(convertedURI.getUserInfo()).isEqualTo(VALID_NODE_ID);
    assertThat(convertedURI.getHost()).isEqualTo(IPV4_ADDRESS);
    assertThat(convertedURI.getPort()).isEqualTo(P2P_PORT);
    assertThat(convertedURI.getQuery()).isEqualTo(DISCOVERY_QUERY);
  }

  @Test
  public void convertEnodeURLWithoutDiscoveryPortShouldBuildExpectedURI() {
    final String value = "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":" + P2P_PORT;
    final URI expectedURI = URI.create(value);

    final URI convertedURI = converter.convert(value);

    assertThat(convertedURI).isEqualTo(expectedURI);
    assertThat(convertedURI.getUserInfo()).isEqualTo(VALID_NODE_ID);
    assertThat(convertedURI.getHost()).isEqualTo(IPV4_ADDRESS);
    assertThat(convertedURI.getPort()).isEqualTo(P2P_PORT);
  }

  @Test
  public void convertEnodeURLWithIPV6ShouldBuildExpectedURI() {
    final String value =
        "enode://"
            + VALID_NODE_ID
            + "@"
            + IPV6_FULL_ADDRESS
            + ":"
            + P2P_PORT
            + "?"
            + DISCOVERY_QUERY;
    final URI expectedURI = URI.create(value);

    final URI convertedURI = converter.convert(value);

    assertThat(convertedURI).isEqualTo(expectedURI);
    assertThat(convertedURI.getUserInfo()).isEqualTo(VALID_NODE_ID);
    assertThat(convertedURI.getHost()).isEqualTo(IPV6_FULL_ADDRESS);
    assertThat(convertedURI.getPort()).isEqualTo(P2P_PORT);
    assertThat(convertedURI.getQuery()).isEqualTo(DISCOVERY_QUERY);
  }

  @Test
  public void convertEnodeURLWithIPV6InCompactFormShouldBuildExpectedURI() {
    final String value =
        "enode://"
            + VALID_NODE_ID
            + "@"
            + IPV6_COMPACT_ADDRESS
            + ":"
            + P2P_PORT
            + "?"
            + DISCOVERY_QUERY;
    final URI expectedURI = URI.create(value);

    final URI convertedURI = converter.convert(value);

    assertThat(convertedURI).isEqualTo(expectedURI);
    assertThat(convertedURI.getUserInfo()).isEqualTo(VALID_NODE_ID);
    assertThat(convertedURI.getHost()).isEqualTo(IPV6_COMPACT_ADDRESS);
    assertThat(convertedURI.getPort()).isEqualTo(P2P_PORT);
    assertThat(convertedURI.getQuery()).isEqualTo(DISCOVERY_QUERY);
  }

  @Test
  public void convertEnodeURLWithoutNodeIdShouldFail() {
    final String value = "enode://@" + IPV4_ADDRESS + ":" + P2P_PORT;
    final Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Invalid enode URL syntax. Enode URL should have the following format 'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'.");
  }

  @Test
  public void convertEnodeURLWithInvalidSizeNodeIdShouldFail() {
    final String value = "enode://wrong_size_string@" + IPV4_ADDRESS + ":" + P2P_PORT;
    final Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Enode URL contains an invalid node ID. Node ID must have 128 characters and shouldn't include the '0x' hex prefix.");
  }

  @Test
  public void convertEnodeURLWithInvalidHexCharacterNodeIdShouldFail() {
    final String value =
        "enode://0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000@"
            + IPV4_ADDRESS
            + ":"
            + P2P_PORT;
    final Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Enode URL contains an invalid node ID. Node ID must have 128 characters and shouldn't include the '0x' hex prefix.");
  }

  @Test
  public void convertEnodeURLWithoutIpShouldFail() {
    final String value = "enode://" + VALID_NODE_ID + "@:" + P2P_PORT;
    final Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid enode URL IP format.");
  }

  @Test
  public void convertEnodeURLWithInvalidIpFormatShouldFail() {
    final String value = "enode://" + VALID_NODE_ID + "@192.0.1:" + P2P_PORT;
    final Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid enode URL IP format.");
  }

  @Test
  public void convertEnodeURLWithoutListeningPortShouldFail() {
    final String value = "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":";
    final Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Invalid enode URL syntax. Enode URL should have the following format 'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'.");
  }

  @Test
  public void convertEnodeURLWithoutListeningPortAndWithDiscoveryPortShouldFail() {
    final String value = "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":?30301";
    final Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Invalid enode URL syntax. Enode URL should have the following format 'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'.");
  }

  @Test
  public void convertEnodeURLWithAboveRangeListeningPortShouldFail() {
    final String value = "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":98765";
    final Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid listening port range. Port should be between 0 - 65535");
  }

  @Test
  public void convertEnodeURLWithAboveRangeDiscoveryPortShouldFail() {
    final String value =
        "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":" + P2P_PORT + "?discport=98765";
    final Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid discovery port range. Port should be between 0 - 65535");
  }

  @Test
  public void convertNullEnodeURLShouldFail() {
    final Throwable thrown = catchThrowable(() -> converter.convert(null));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Can't convert null/empty string to EnodeURLProperty.");
  }

  @Test
  public void convertEmptyEnodeURLShouldFail() {
    final Throwable thrown = catchThrowable(() -> converter.convert(""));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Can't convert null/empty string to EnodeURLProperty.");
  }
}
