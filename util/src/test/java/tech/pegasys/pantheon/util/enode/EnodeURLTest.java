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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
  public void new_withMatchingDiscoveryAndListeningPorts() {
    final EnodeURL enode =
        EnodeURL.builder()
            .nodeId(VALID_NODE_ID)
            .ipAddress(IPV4_ADDRESS)
            .listeningPort(P2P_PORT)
            .discoveryPort(OptionalInt.of(P2P_PORT))
            .build();
    assertThat(enode.getListeningPort()).isEqualTo(P2P_PORT);
    // A discovery port matching the listening port should not be explicitly specified
    assertThat(enode.getDiscoveryPort()).isEmpty();
  }

  @Test
  public void new_withNonMatchingDiscoveryAndListeningPorts() {
    final EnodeURL enode =
        EnodeURL.builder()
            .nodeId(VALID_NODE_ID)
            .ipAddress(IPV4_ADDRESS)
            .listeningPort(P2P_PORT)
            .discoveryPort(OptionalInt.of(DISCOVERY_PORT))
            .build();
    assertThat(enode.getListeningPort()).isEqualTo(P2P_PORT);
    // A discovery port matching the listening port should not be explicitly specified
    assertThat(enode.getDiscoveryPort()).isEqualTo(OptionalInt.of(DISCOVERY_PORT));
  }

  @Test
  public void fromString_withDiscoveryPortShouldBuildExpectedEnodeURLObject() {
    final EnodeURL expectedEnodeURL =
        EnodeURL.builder()
            .nodeId(VALID_NODE_ID)
            .ipAddress(IPV4_ADDRESS)
            .listeningPort(P2P_PORT)
            .discoveryPort(OptionalInt.of(DISCOVERY_PORT))
            .build();
    final String enodeURLString =
        "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":" + P2P_PORT + "?" + DISCOVERY_QUERY;

    final EnodeURL enodeURL = EnodeURL.fromString(enodeURLString);

    assertThat(enodeURL).isEqualTo(expectedEnodeURL);
  }

  @Test
  public void fromString_withoutDiscoveryPortShouldBuildExpectedEnodeURLObject() {
    final EnodeURL expectedEnodeURL =
        EnodeURL.builder()
            .nodeId(VALID_NODE_ID)
            .ipAddress(IPV4_ADDRESS)
            .listeningPort(P2P_PORT)
            .build();
    final String enodeURLString = "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":" + P2P_PORT;

    final EnodeURL enodeURL = EnodeURL.fromString(enodeURLString);

    assertThat(enodeURL).isEqualTo(expectedEnodeURL);
  }

  @Test
  public void fromString_withIPV6ShouldBuildExpectedEnodeURLObject() {
    final EnodeURL expectedEnodeURL =
        EnodeURL.builder()
            .nodeId(VALID_NODE_ID)
            .ipAddress(IPV6_FULL_ADDRESS)
            .listeningPort(P2P_PORT)
            .discoveryPort(OptionalInt.of(DISCOVERY_PORT))
            .build();
    final String enodeURLString =
        "enode://"
            + VALID_NODE_ID
            + "@"
            + IPV6_FULL_ADDRESS
            + ":"
            + P2P_PORT
            + "?"
            + DISCOVERY_QUERY;

    final EnodeURL enodeURL = EnodeURL.fromString(enodeURLString);

    assertThat(enodeURL).isEqualTo(expectedEnodeURL);
  }

  @Test
  public void fromString_ithIPV6InCompactFormShouldBuildExpectedEnodeURLObject() {
    final EnodeURL expectedEnodeURL =
        EnodeURL.builder()
            .nodeId(VALID_NODE_ID)
            .ipAddress(IPV6_COMPACT_ADDRESS)
            .listeningPort(P2P_PORT)
            .discoveryPort(OptionalInt.of(DISCOVERY_PORT))
            .build();
    final String enodeURLString =
        "enode://"
            + VALID_NODE_ID
            + "@"
            + IPV6_COMPACT_ADDRESS
            + ":"
            + P2P_PORT
            + "?"
            + DISCOVERY_QUERY;

    final EnodeURL enodeURL = EnodeURL.fromString(enodeURLString);

    assertThat(enodeURL).isEqualTo(expectedEnodeURL);
  }

  @Test
  public void fromString_withoutNodeIdShouldFail() {
    final String enodeURLString = "enode://@" + IPV4_ADDRESS + ":" + P2P_PORT;
    final Throwable thrown = catchThrowable(() -> EnodeURL.fromString(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing node ID.");
  }

  @Test
  public void fromString_withInvalidSizeNodeIdShouldFail() {
    final String enodeURLString = "enode://wrong_size_string@" + IPV4_ADDRESS + ":" + P2P_PORT;
    final Throwable thrown = catchThrowable(() -> EnodeURL.fromString(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid node ID");
  }

  @Test
  public void fromString_withInvalidHexCharacterNodeIdShouldFail() {
    final String enodeURLString =
        "enode://0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000@"
            + IPV4_ADDRESS
            + ":"
            + P2P_PORT;
    final Throwable thrown = catchThrowable(() -> EnodeURL.fromString(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid node ID");
  }

  @Test
  public void fromString_withoutIpShouldFail() {
    final String enodeURLString = "enode://" + VALID_NODE_ID + "@:" + P2P_PORT;
    final Throwable thrown = catchThrowable(() -> EnodeURL.fromString(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing or invalid ip address.");
  }

  @Test
  public void fromString_withInvalidIpFormatShouldFail() {
    final String enodeURLString = "enode://" + VALID_NODE_ID + "@192.0.1:" + P2P_PORT;
    final Throwable thrown = catchThrowable(() -> EnodeURL.fromString(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing or invalid ip address.");
  }

  @Test
  public void fromString_withoutListeningPortShouldFail() {
    final String enodeURLString = "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":";
    final Throwable thrown = catchThrowable(() -> EnodeURL.fromString(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid listening port.");
  }

  @Test
  public void fromString_withoutListeningPortAndWithDiscoveryPortShouldFail() {
    final String enodeURLString =
        "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":?discport=30301";
    final Throwable thrown = catchThrowable(() -> EnodeURL.fromString(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid listening port.");
  }

  @Test
  public void fromString_withAboveRangeListeningPortShouldFail() {
    final String enodeURLString = "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":98765";
    final Throwable thrown = catchThrowable(() -> EnodeURL.fromString(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid listening port.");
  }

  @Test
  public void fromString_withAboveRangeDiscoveryPortShouldFail() {
    final String enodeURLString =
        "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":" + P2P_PORT + "?discport=98765";
    final Throwable thrown = catchThrowable(() -> EnodeURL.fromString(enodeURLString));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid discovery port.");
  }

  @Test
  public void fromString_withMisspelledDiscoveryParam() {
    final String query = "adiscport=1234";
    final String enodeURLString =
        "enode://" + VALID_NODE_ID + "@" + IPV6_FULL_ADDRESS + ":" + P2P_PORT + "?" + query;

    assertThatThrownBy(() -> EnodeURL.fromString(enodeURLString))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid discovery port: '" + query + "'");
  }

  @Test
  public void fromString_withAdditionalTrailingQueryParam() {
    final String query = "discport=1234&other=y";
    final String enodeURLString =
        "enode://" + VALID_NODE_ID + "@" + IPV6_FULL_ADDRESS + ":" + P2P_PORT + "?" + query;

    assertThatThrownBy(() -> EnodeURL.fromString(enodeURLString))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid discovery port: '" + query + "'");
  }

  @Test
  public void fromString_withAdditionalLeadingQueryParam() {
    final String query = "other=123&discport=1234";
    final String enodeURLString =
        "enode://" + VALID_NODE_ID + "@" + IPV6_FULL_ADDRESS + ":" + P2P_PORT + "?" + query;

    assertThatThrownBy(() -> EnodeURL.fromString(enodeURLString))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid discovery port: '" + query + "'");
  }

  @Test
  public void fromString_withAdditionalLeadingAndTrailingQueryParams() {
    final String query = "other=123&discport=1234&other2=456";
    final String enodeURLString =
        "enode://" + VALID_NODE_ID + "@" + IPV6_FULL_ADDRESS + ":" + P2P_PORT + "?" + query;

    assertThatThrownBy(() -> EnodeURL.fromString(enodeURLString))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid discovery port: '" + query + "'");
  }

  @Test
  public void fromString_withMultipleDiscoveryParams() {
    final String query = "discport=1234&discport=456";
    final String enodeURLString =
        "enode://" + VALID_NODE_ID + "@" + IPV6_FULL_ADDRESS + ":" + P2P_PORT + "?" + query;

    assertThatThrownBy(() -> EnodeURL.fromString(enodeURLString))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid discovery port: '" + query + "'");
  }

  @Test
  public void fromString_withNullEnodeURLShouldFail() {
    final Throwable thrown = catchThrowable(() -> EnodeURL.fromString(null));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid empty value");
  }

  @Test
  public void fromString_withEmptyEnodeURLShouldFail() {
    final Throwable thrown = catchThrowable(() -> EnodeURL.fromString(""));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid empty value.");
  }

  @Test
  public void fromString_withWhitespaceEnodeURLShouldFail() {
    final Throwable thrown = catchThrowable(() -> EnodeURL.fromString(" "));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid empty value.");
  }

  @Test
  public void toURI_WithDiscoveryPortCreateExpectedURI() {
    final String enodeURLString =
        "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":" + P2P_PORT + "?" + DISCOVERY_QUERY;
    final URI expectedURI = URI.create(enodeURLString);
    final URI createdURI = EnodeURL.fromString(enodeURLString).toURI();

    assertThat(createdURI).isEqualTo(expectedURI);
  }

  @Test
  public void toURI_WithoutDiscoveryPortCreateExpectedURI() {
    final String enodeURLString = "enode://" + VALID_NODE_ID + "@" + IPV4_ADDRESS + ":" + P2P_PORT;
    final URI expectedURI = URI.create(enodeURLString);
    final URI createdURI = EnodeURL.fromString(enodeURLString).toURI();

    assertThat(createdURI).isEqualTo(expectedURI);
  }

  @Test
  public void getEffectiveDiscoveryPort_withMatchingDiscoveryAndListeningPorts() {
    final EnodeURL enode =
        EnodeURL.builder()
            .nodeId(VALID_NODE_ID)
            .ipAddress(IPV4_ADDRESS)
            .listeningPort(P2P_PORT)
            .discoveryPort(OptionalInt.of(P2P_PORT))
            .build();
    assertThat(enode.getListeningPort()).isEqualTo(P2P_PORT);
    // A discovery port matching the listening port should not be explicitly specified
    assertThat(enode.getDiscoveryPort()).isEmpty();
    assertThat(enode.getEffectiveDiscoveryPort()).isEqualTo(P2P_PORT);
  }

  @Test
  public void getEffectiveDiscoveryPort_withDistinctDiscoveryAndListeningPorts() {
    final EnodeURL enode =
        EnodeURL.builder()
            .nodeId(VALID_NODE_ID)
            .ipAddress(IPV4_ADDRESS)
            .listeningPort(P2P_PORT)
            .discoveryPort(OptionalInt.of(DISCOVERY_PORT))
            .build();
    assertThat(enode.getListeningPort()).isEqualTo(P2P_PORT);
    // A discovery port matching the listening port should not be explicitly specified
    assertThat(enode.getDiscoveryPort()).isEqualTo(OptionalInt.of(DISCOVERY_PORT));
    assertThat(enode.getEffectiveDiscoveryPort()).isEqualTo(DISCOVERY_PORT);
  }
}
