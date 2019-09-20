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
import static java.nio.file.Files.createTempDirectory;

import java.net.URL;
import org.hyperledger.besu.cli.subcommands.networkcreate.mapping.InitConfigurationErrorHandler;
import org.hyperledger.besu.cli.subcommands.networkcreate.mapping.MapperAdapter;
import org.hyperledger.besu.cli.subcommands.networkcreate.model.Configuration;

import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.Before;
import org.junit.Test;

public class NetworkCreateSubCommandTest {

  private static final Logger LOG = LogManager.getLogger();

  private Path tmpOutputDirectoryPath;

  @Before
  public void init() throws IOException {
    tmpOutputDirectoryPath = createTempDirectory(format("output-%d", currentTimeMillis()));
    Configurator.setAllLevels("", Level.ALL);
  }

  @Test
  public void jsonTest() throws Exception {
    generate(this.getClass().getResource("/networkcreate/test.json"));
  }

  @Test
  public void yamlTest() throws Exception {
    generate(this.getClass().getResource("/networkcreate/test.yaml"));
  }

  @Test
  public void tomlTest() throws Exception {
    generate(this.getClass().getResource("/networkcreate/test.toml"));
  }

  private void generate(URL fileURL) throws Exception {
    final MapperAdapter mapper = MapperAdapter.getMapper(fileURL);
    final Configuration initConfig = mapper.map(new TypeReference<>() {});
    initConfig.verify(new InitConfigurationErrorHandler());
    // TODO remove debug print
    System.out.println(mapper.writeValueAsString(initConfig));

    LOG.debug("Resources generated in {}", initConfig.generate(tmpOutputDirectoryPath));
  }
}
