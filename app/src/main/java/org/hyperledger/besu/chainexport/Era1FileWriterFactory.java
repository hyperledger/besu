/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.chainexport;

import org.hyperledger.besu.util.io.OutputStreamFactory;
import org.hyperledger.besu.util.snappy.SnappyFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/** Factory for producing Era1FileWriters */
public class Era1FileWriterFactory {
  private final OutputStreamFactory outputStreamFactory;
  private final SnappyFactory snappyFactory;

  /**
   * Constructs an Era1FileWriterFactory using the supplied parameters
   *
   * @param outputStreamFactory an OutputStreamFactory
   * @param snappyFactory a SnappyFactory
   */
  public Era1FileWriterFactory(
      final OutputStreamFactory outputStreamFactory, final SnappyFactory snappyFactory) {
    this.outputStreamFactory = outputStreamFactory;
    this.snappyFactory = snappyFactory;
  }

  /**
   * Produces a new Era1FileWriter to write to the supplied file
   *
   * @param file The file for the Era1FileWriter to write to
   * @return A new Era1FileWriter to write to the supplied file
   * @throws FileNotFoundException if the supplied file is not found or cannot be created
   */
  public Era1FileWriter era1FileWriter(final File file) throws FileNotFoundException {
    FileOutputStream fileOutputStream = outputStreamFactory.createFileOutputStream(file);
    return new Era1FileWriter(fileOutputStream, snappyFactory);
  }
}
