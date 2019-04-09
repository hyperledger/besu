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
package tech.pegasys.orion.testutil;

import static com.google.common.io.Files.readLines;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Charsets;
import net.consensys.orion.cmd.Orion;
import net.consensys.orion.config.Config;
import okhttp3.HttpUrl;

public class OrionTestHarness {

  private final Orion orion;
  private final Config config;

  protected static final String HOST = "127.0.0.1";

  protected OrionTestHarness(final Orion orion, final Config config) {

    this.orion = orion;
    this.config = config;
  }

  public Orion getOrion() {
    return orion;
  }

  public Config getConfig() {
    return config;
  }

  public List<String> getPublicKeys() {
    return config.publicKeys().stream()
        .map(OrionTestHarness::readFile)
        .collect(Collectors.toList());
  }

  private static String readFile(final Path path) {
    try {
      return readLines(path.toFile(), Charsets.UTF_8).get(0);
    } catch (IOException e) {
      e.printStackTrace();
      return "";
    }
  }

  public URI clientUrl() {
    HttpUrl httpUrl =
        new HttpUrl.Builder().scheme("http").host(HOST).port(orion.clientPort()).build();

    return URI.create(httpUrl.toString());
  }

  public String nodeUrl() {
    return new HttpUrl.Builder()
        .scheme("http")
        .host(HOST)
        .port(orion.nodePort())
        .build()
        .toString();
  }
}
