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
package org.hyperledger.besu.services.tasks;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlatFileTaskCollection<T> implements TaskCollection<T> {
  private static final Logger LOG = LoggerFactory.getLogger(FlatFileTaskCollection.class);
  private static final long DEFAULT_FILE_ROLL_SIZE_BYTES = 1024 * 1024 * 10; // 10Mb
  static final String FILENAME_PREFIX = "tasks";
  private final Set<FlatFileTask<T>> outstandingTasks = new HashSet<>();

  private final Path storageDirectory;
  private final Function<T, Bytes> serializer;
  private final Function<Bytes, T> deserializer;
  private final long rollWhenFileSizeExceedsBytes;

  private final ByteBuffer lengthBuffer = ByteBuffer.allocate(Integer.BYTES);

  private FileChannel readFileChannel;
  private FileChannel writeFileChannel;

  private long size = 0;
  private int readFileNumber = 0;
  private int writeFileNumber = 0;

  public FlatFileTaskCollection(
      final Path storageDirectory,
      final Function<T, Bytes> serializer,
      final Function<Bytes, T> deserializer) {
    this(storageDirectory, serializer, deserializer, DEFAULT_FILE_ROLL_SIZE_BYTES);
  }

  FlatFileTaskCollection(
      final Path storageDirectory,
      final Function<T, Bytes> serializer,
      final Function<Bytes, T> deserializer,
      final long rollWhenFileSizeExceedsBytes) {
    this.storageDirectory = storageDirectory;
    this.serializer = serializer;
    this.deserializer = deserializer;
    this.rollWhenFileSizeExceedsBytes = rollWhenFileSizeExceedsBytes;
    writeFileChannel = openWriteFileChannel(writeFileNumber);
    readFileChannel = openReadFileChannel(readFileNumber);
  }

  private FileChannel openReadFileChannel(final int fileNumber) {
    try {
      return FileChannel.open(
          pathForFileNumber(fileNumber),
          StandardOpenOption.DELETE_ON_CLOSE,
          StandardOpenOption.READ);
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }

  private FileChannel openWriteFileChannel(final int fileNumber) {
    try {
      return FileChannel.open(
          pathForFileNumber(fileNumber),
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE,
          StandardOpenOption.CREATE);
    } catch (final IOException e) {
      throw new StorageException(
          "There was a problem opening FileChannel " + pathForFileNumber(fileNumber), e);
    }
  }

  @Override
  public synchronized void add(final T taskData) {
    final Bytes data = serializer.apply(taskData);
    try {
      writeTaskData(data);
      size++;
      if (writeFileChannel.size() > rollWhenFileSizeExceedsBytes) {
        LOG.debug("Writing reached end of file {}", writeFileNumber);
        writeFileChannel.close();
        writeFileNumber++;
        writeFileChannel = openWriteFileChannel(writeFileNumber);
      }
    } catch (final IOException e) {
      throw new StorageException(
          "There was a problem adding to FileChannel " + pathForFileNumber(writeFileNumber), e);
    }
  }

  @Override
  public synchronized Task<T> remove() {
    if (isEmpty()) {
      return null;
    }
    try {
      final ByteBuffer dataBuffer = readNextTaskData();
      final T data = deserializer.apply(Bytes.wrapByteBuffer(dataBuffer));
      final FlatFileTask<T> task = new FlatFileTask<>(this, data);
      outstandingTasks.add(task);
      size--;
      return task;
    } catch (final IOException e) {
      throw new StorageException(
          "There was a problem removing from FileChannel " + pathForFileNumber(readFileNumber), e);
    }
  }

  private ByteBuffer readNextTaskData() throws IOException {
    final int dataLength = readDataLength();
    final ByteBuffer dataBuffer = ByteBuffer.allocate(dataLength);
    readBytes(dataBuffer, dataLength);
    return dataBuffer;
  }

  private void writeTaskData(final Bytes data) throws IOException {
    final long offset = writeFileChannel.size();
    writeDataLength(data.size(), offset);
    writeFileChannel.write(ByteBuffer.wrap(data.toArrayUnsafe()), offset + Integer.BYTES);
  }

  private int readDataLength() throws IOException {
    lengthBuffer.position(0);
    lengthBuffer.limit(Integer.BYTES);
    readBytes(lengthBuffer, Integer.BYTES);
    return lengthBuffer.getInt(0);
  }

  private void writeDataLength(final int size, final long offset) throws IOException {
    lengthBuffer.position(0);
    lengthBuffer.putInt(size);
    lengthBuffer.flip();
    writeFileChannel.write(lengthBuffer, offset);
  }

  private void readBytes(final ByteBuffer buffer, final int expectedLength) throws IOException {
    int readBytes = readFileChannel.read(buffer);

    if (readBytes == -1 && writeFileNumber > readFileNumber) {
      LOG.debug("Reading reached end of file {}", readFileNumber);
      readFileChannel.close();
      readFileNumber++;
      readFileChannel = openReadFileChannel(readFileNumber);

      readBytes = readFileChannel.read(buffer);
    }
    if (readBytes != expectedLength) {
      throw new IllegalStateException(
          "Task queue corrupted. Expected to read "
              + expectedLength
              + " bytes but only got "
              + readBytes);
    }
  }

  @Override
  public synchronized long size() {
    return size;
  }

  @Override
  public synchronized boolean isEmpty() {
    return size() == 0;
  }

  @VisibleForTesting
  int getReadFileNumber() {
    return readFileNumber;
  }

  @VisibleForTesting
  int getWriteFileNumber() {
    return writeFileNumber;
  }

  @Override
  public synchronized void clear() {
    outstandingTasks.clear();
    try {
      readFileChannel.close();
      writeFileChannel.close();
      for (int i = readFileNumber; i <= writeFileNumber; i++) {
        final File file = pathForFileNumber(i).toFile();
        if (!file.delete() && file.exists()) {
          LOG.error("Failed to delete tasks file {}", file.getAbsolutePath());
        }
      }
      readFileNumber = 0;
      writeFileNumber = 0;
      writeFileChannel = openWriteFileChannel(writeFileNumber);
      readFileChannel = openReadFileChannel(readFileNumber);
      size = 0;
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public synchronized boolean allTasksCompleted() {
    return isEmpty() && outstandingTasks.isEmpty();
  }

  @Override
  public synchronized void close() {
    try {
      readFileChannel.close();
      writeFileChannel.close();
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }

  private Path pathForFileNumber(final int fileNumber) {
    return storageDirectory.resolve(FILENAME_PREFIX + fileNumber);
  }

  private synchronized boolean markTaskCompleted(final FlatFileTask<T> task) {
    return outstandingTasks.remove(task);
  }

  private synchronized void handleFailedTask(final FlatFileTask<T> task) {
    if (markTaskCompleted(task)) {
      add(task.getData());
    }
  }

  public static class StorageException extends RuntimeException {
    StorageException(final Throwable t) {
      super(t);
    }

    StorageException(final String m, final Throwable t) {
      super(m, t);
    }
  }

  private static class FlatFileTask<T> implements Task<T> {
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final FlatFileTaskCollection<T> parentQueue;
    private final T data;

    private FlatFileTask(final FlatFileTaskCollection<T> parentQueue, final T data) {
      this.parentQueue = parentQueue;
      this.data = data;
    }

    @Override
    public T getData() {
      return data;
    }

    @Override
    public void markCompleted() {
      if (completed.compareAndSet(false, true)) {
        parentQueue.markTaskCompleted(this);
      }
    }

    @Override
    public void markFailed() {
      if (completed.compareAndSet(false, true)) {
        parentQueue.handleFailedTask(this);
      }
    }
  }
}
