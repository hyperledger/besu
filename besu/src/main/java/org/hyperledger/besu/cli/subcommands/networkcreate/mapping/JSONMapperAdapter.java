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
package org.hyperledger.besu.cli.subcommands.networkcreate.mapping;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URL;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.io.Resources;
// TODO Handle errors
class JSONMapperAdapter extends MapperAdapter {
  private final URL fileURL;

  JSONMapperAdapter(String initFile) {
    fileURL = this.getClass().getResource(initFile);
  }

  public <T> T map(TypeReference<T> clazz) throws IOException {
    mapper = getMapper(new JsonFactory());
    final String yaml = Resources.toString(fileURL, UTF_8);
    return mapper.readValue(yaml, clazz);
  }
}
