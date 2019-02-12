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
package tech.pegasys.pantheon.util.enode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.net.URI;
import java.util.OptionalInt;

import org.junit.Test;

public class EnodeURLTest {

  private final String VALID_NODE_ID =
      "6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0";
  private final String IPV4_ADDRESS = "192.168.0.1";
  private final String IPV6_FULL_ADDRESS = "[2001:db8:85a3:0:0:8a2e:0370:7334]";
  private final String IPV6_COMPACT_ADDRESS = "[2001:db8:85a3::8a2e:0370:7334]";
  private final int P2P_PORT = 30303;
  private final int DISCOVERY_PORT = 30301;
  private final String DISCOVERY_QUERY = "discport=" + DISCOVERY_PORT;

  @Test
  public void createEnodeURLWithDiscoveryPortShouldBuildExpectedEnodeURLObject() {
    final EnodeURL expectedEnodeURL =
        new EnodeURL(VALID_NODE_ID, IPV4_ADDRESS, P2P_PORT, OptionalInt.of(DISCOVERY_PORT));
    final String enodeURLString =
        "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":" + P2P_PORT + "?" + DISCOVERY_QUERY;

    final EnodeURL enodeURL = new EnodeURL(enodeURLString);

    assertThat(enodeURL).isEqualTo(expectedEnodeURL);
  }

  @Test
  public void createEnodeURLWithoutDiscoveryPortShouldBuildExpectedEnodeURLObject() {
    final EnodeURL expectedEnodeURL = new EnodeURL(VALID_NODE_ID, IPV4_ADDRESS, P2P_PORT);
    final String enodeURLString = "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":" + P2P_PORT;

    final EnodeURL enodeURL = new EnodeURL(enodeURLString);

    assertThat(enodeURL).isEqualTo(expectedEnodeURL);
  }

  @Test
  public void createEnodeURLWithIPV6ShouldBuildExpectedEnodeURLObject() {
    final EnodeURL expectedEnodeURL =
        new EnodeURL(VALID_NODE_ID, IPV6_FULL_ADDRESS, P2P_PORT, OptionalInt.of(DISCOVERY_PORT));
    final String enodeURLString =
        "enode://"
            + VALID_NODE_ID
            + "@"
            + IPV6_FULL_ADDRESS
            + ":"
            + P2P_PORT
            + "?"
            + DISCOVERY_QUERY;

    final EnodeURL enodeURL = new EnodeURL(enodeURLString);

    assertThat(enodeURL).isEqualTo(expectedEnodeURL);
  }

  @Test
  public void createEnodeURLWithIPV6InCompactFormShouldBuildExpectedEnodeURLObject() {
    final EnodeURL expectedEnodeURL =
        new EnodeURL(VALID_NODE_ID, IPV6_COMPACT_ADDRESS, P2P_PORT, OptionalInt.of(DISCOVERY_PORT));
    final String enodeURLString =
        "enode://"
            + VALID_NODE_ID
            + "@"
            + IPV6_COMPACT_ADDRESS
            + ":"
            + P2P_PORT
            + "?"
            + DISCOVERY_QUERY;

    final EnodeURL enodeURL = new EnodeURL(enodeURLString);

    assertThat(enodeURL).isEqualTo(expectedEnodeURL);
  }

  @Test
  public void createEnodeURLWithoutNodeIdShouldFail() {
    final String enodeURLString = "enode://@" + IPV4_ADDRESS + ":" + P2P_PORT;
    final Throwable thrown = catchThrowable(() -> new EnodeURL(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Invalid enode URL syntax. Enode URL should have the following format 'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'.");
  }

  @Test
  public void createEnodeURLWithInvalidSizeNodeIdShouldFail() {
    final String enodeURLString = "enode://wrong_size_string@" + IPV4_ADDRESS + ":" + P2P_PORT;
    final Throwable thrown = catchThrowable(() -> new EnodeURL(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Enode URL contains an invalid node ID. Node ID must have 128 characters and shouldn't include the '0x' hex prefix.");
  }

  @Test
  public void createEnodeURLWithInvalidHexCharacterNodeIdShouldFail() {
    final String enodeURLString =
        "enode://0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000@"
            + IPV4_ADDRESS
            + ":"
            + P2P_PORT;
    final Throwable thrown = catchThrowable(() -> new EnodeURL(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Enode URL contains an invalid node ID. Node ID must have 128 characters and shouldn't include the '0x' hex prefix.");
  }

  @Test
  public void createEnodeURLWithoutIpShouldFail() {
    final String enodeURLString = "enode://" + VALID_NODE_ID + "@:" + P2P_PORT;
    final Throwable thrown = catchThrowable(() -> new EnodeURL(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid enode URL IP format.");
  }

  @Test
  public void createEnodeURLWithInvalidIpFormatShouldFail() {
    final String enodeURLString = "enode://" + VALID_NODE_ID + "@192.0.1:" + P2P_PORT;
    final Throwable thrown = catchThrowable(() -> new EnodeURL(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid enode URL IP format.");
  }

  @Test
  public void createEnodeURLWithoutListeningPortShouldFail() {
    final String enodeURLString = "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":";
    final Throwable thrown = catchThrowable(() -> new EnodeURL(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Invalid enode URL syntax. Enode URL should have the following format 'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'.");
  }

  @Test
  public void createEnodeURLWithoutListeningPortAndWithDiscoveryPortShouldFail() {
    final String enodeURLString = "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":?30301";
    final Throwable thrown = catchThrowable(() -> new EnodeURL(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Invalid enode URL syntax. Enode URL should have the following format 'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'.");
  }

  @Test
  public void createEnodeURLWithAboveRangeListeningPortShouldFail() {
    final String enodeURLString = "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":98765";
    final Throwable thrown = catchThrowable(() -> new EnodeURL(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid listening port range. Port should be between 0 - 65535");
  }

  @Test
  public void createEnodeURLWithAboveRangeDiscoveryPortShouldFail() {
    final String enodeURLString =
        "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":" + P2P_PORT + "?discport=98765";
    final Throwable thrown = catchThrowable(() -> new EnodeURL(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid discovery port range. Port should be between 0 - 65535");
  }

  @Test
  public void createEnodeURLWithNullEnodeURLShouldFail() {
    final Throwable thrown = catchThrowable(() -> new EnodeURL(null));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Can't convert null/empty string to EnodeURLProperty.");
  }

  @Test
  public void createEnodeURLWithEmptyEnodeURLShouldFail() {
    final Throwable thrown = catchThrowable(() -> new EnodeURL(""));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Can't convert null/empty string to EnodeURLProperty.");
  }

  @Test
  public void toURIWithDiscoveryPortCreateExpectedURI() {
    final String enodeURLString =
        "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":" + P2P_PORT + "?" + DISCOVERY_QUERY;
    final URI expectedURI = URI.create(enodeURLString);
    final URI createdURI = new EnodeURL(enodeURLString).toURI();

    assertThat(createdURI).isEqualTo(expectedURI);
  }

  @Test
  public void toURIWithoutDiscoveryPortCreateExpectedURI() {
    final String enodeURLString = "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":" + P2P_PORT;
    final URI expectedURI = URI.create(enodeURLString);
    final URI createdURI = new EnodeURL(enodeURLString).toURI();

    assertThat(createdURI).isEqualTo(expectedURI);
  }
}
