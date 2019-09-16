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
package org.hyperledger.besu.cli.subcommands.networkcreate;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createTempDirectory;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Wei;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.io.Resources;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.Before;
import org.junit.Test;

public class NetworkCreateSubCommandTest {

  private Path tmpOutputDirectoryPath;

  @Before
  public void init() throws IOException {
    tmpOutputDirectoryPath = createTempDirectory(format("output-%d", currentTimeMillis()));
    Configurator.setAllLevels("", Level.ALL);
  }

  @Test
  public void jacksonTest() throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    mapper.configure(Feature.STRICT_DUPLICATE_DETECTION, true);

    mapper.registerModule(new Jdk8Module());

    SimpleModule addressModule = new SimpleModule("CustomAddressSerializer");
    addressModule.addSerializer(Address.class, new CustomAddressSerializer());
    mapper.registerModule(addressModule);

    SimpleModule balanceModule = new SimpleModule("CustomBalanceSerializer");
    balanceModule.addSerializer(Wei.class, new CustomBalanceSerializer());
    mapper.registerModule(balanceModule);

    final URL initConfigFile = this.getClass().getResource("/networkcreate/test.yaml");
    final String yaml = Resources.toString(initConfigFile, UTF_8);
    InitConfiguration initConfig = mapper.readValue(yaml, InitConfiguration.class);

    initConfig.verify(new InitConfigurationErrorHandler());
    // TODO remove debug print
    System.out.println(mapper.writeValueAsString(initConfig));

    initConfig.generate(tmpOutputDirectoryPath);
  }
}
