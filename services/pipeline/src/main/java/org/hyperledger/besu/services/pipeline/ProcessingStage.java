/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.services.pipeline;

class ProcessingStage<I, O> implements Stage {

  private final String name;
  private final ReadPipe<I> inputPipe;
  private final WritePipe<O> outputPipe;
  private final Processor<I, O> processor;

  public ProcessingStage(
      final String name,
      final ReadPipe<I> inputPipe,
      final WritePipe<O> outputPipe,
      final Processor<I, O> processor) {
    this.name = name;
    this.inputPipe = inputPipe;
    this.outputPipe = outputPipe;
    this.processor = processor;
  }

  @Override
  public void run() {
    while (inputPipe.hasMore()) {
      processor.processNextInput(inputPipe, outputPipe);
    }
    if (inputPipe.isAborted()) {
      processor.abort();
    }
    while (!processor.attemptFinalization(outputPipe)) {
      if (inputPipe.isAborted()) {
        processor.abort();
        break;
      }
    }
    outputPipe.close();
  }

  @Override
  public String getName() {
    return name;
  }
}
