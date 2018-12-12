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
  private final EnodeToURIPropertyConverter converter = new EnodeToURIPropertyConverter();

  @Test
  public void convertEnodeURLWithDiscoveryPortShouldBuildExpectedURI() {
    String value = "enode://" + VALID_NODE_ID + "@192.168.0.1:30303?discport=30301";
    URI expectedURI = URI.create(value);

    URI convertedURI = converter.convert(value);

    assertThat(convertedURI).isEqualTo(expectedURI);
  }

  @Test
  public void convertEnodeURLWithoutDiscoveryPortShouldBuildExpectedURI() {
    String value = "enode://" + VALID_NODE_ID + "@192.168.0.1:30303";
    URI expectedURI = URI.create(value);

    URI convertedURI = converter.convert(value);

    assertThat(convertedURI).isEqualTo(expectedURI);
  }

  @Test
  public void convertEnodeURLWithIPV6ShouldBuildExpectedURI() {
    String value =
        "enode://" + VALID_NODE_ID + "@2001:0db8:85a3:0:0:8a2e:0370:7334:30303?discport=30301";
    URI expectedURI = URI.create(value);

    URI convertedURI = converter.convert(value);

    assertThat(convertedURI).isEqualTo(expectedURI);
  }

  @Test
  public void convertEnodeURLWithIPV6InCompactFormShouldBuildExpectedURI() {
    String value = "enode://" + VALID_NODE_ID + "@fe80::200:f8ff:fe21:67cf:30303?discport=30301";
    URI expectedURI = URI.create(value);

    URI convertedURI = converter.convert(value);

    assertThat(convertedURI).isEqualTo(expectedURI);
  }

  @Test
  public void convertEnodeURLWithoutNodeIdShouldFail() {
    String value = "enode://@192.168.0.1:30303";
    Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Invalid enode URL syntax. Enode URL should have the following format 'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'.");
  }

  @Test
  public void convertEnodeURLWithInvalidSizeNodeIdShouldFail() {
    String value = "enode://wrong_size_string@192.168.0.1:30303";
    Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Enode URL contains an invalid node ID. Node ID must have 128 characters and shouldn't include the '0x' hex prefix.");
  }

  @Test
  public void convertEnodeURLWithInvalidHexCharacterNodeIdShouldFail() {
    String value =
        "enode://0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000@192.168.0.1:30303";
    Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Enode URL contains an invalid node ID. Node ID must have 128 characters and shouldn't include the '0x' hex prefix.");
  }

  @Test
  public void convertEnodeURLWithoutIpShouldFail() {
    String value = "enode://" + VALID_NODE_ID + "@:30303";
    Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid enode URL IP format.");
  }

  @Test
  public void convertEnodeURLWithInvalidIpFormatShouldFail() {
    String value = "enode://" + VALID_NODE_ID + "@192.0.1:30303";
    Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid enode URL IP format.");
  }

  @Test
  public void convertEnodeURLWithoutListeningPortShouldFail() {
    String value = "enode://" + VALID_NODE_ID + "@192.168.0.1:";
    Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Invalid enode URL syntax. Enode URL should have the following format 'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'.");
  }

  @Test
  public void convertEnodeURLWithoutListeningPortAndWithDiscoveryPortShouldFail() {
    String value = "enode://" + VALID_NODE_ID + "@192.168.0.1:?30301";
    Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Invalid enode URL syntax. Enode URL should have the following format 'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'.");
  }

  @Test
  public void convertEnodeURLWithAboveRangeListeningPortShouldFail() {
    String value = "enode://" + VALID_NODE_ID + "@192.168.0.1:98765";
    Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid listening port range. Port should be between 0 - 65535");
  }

  @Test
  public void convertEnodeURLWithAboveRangeDiscoveryPortShouldFail() {
    String value = "enode://" + VALID_NODE_ID + "@192.168.0.1:30303?discport=98765";
    Throwable thrown = catchThrowable(() -> converter.convert(value));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid discovery port range. Port should be between 0 - 65535");
  }

  @Test
  public void convertNullEnodeURLShouldFail() {
    Throwable thrown = catchThrowable(() -> converter.convert(null));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Can't convert null/empty string to EnodeURLProperty.");
  }

  @Test
  public void convertEmptyEnodeURLShouldFail() {
    Throwable thrown = catchThrowable(() -> converter.convert(""));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Can't convert null/empty string to EnodeURLProperty.");
  }
}
