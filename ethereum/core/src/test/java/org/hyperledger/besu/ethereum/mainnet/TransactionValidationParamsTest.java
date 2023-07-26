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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class TransactionValidationParamsTest {

  @Test
  public void isAllowFutureNonce() {
    assertThat(
            ImmutableTransactionValidationParams.builder()
                .isAllowFutureNonce(true)
                .build()
                .isAllowFutureNonce())
        .isTrue();
    assertThat(
            ImmutableTransactionValidationParams.builder()
                .isAllowFutureNonce(false)
                .build()
                .isAllowFutureNonce())
        .isFalse();
  }

  @Test
  public void checkOnchainPermissions() {
    assertThat(
            ImmutableTransactionValidationParams.builder()
                .checkOnchainPermissions(true)
                .build()
                .checkOnchainPermissions())
        .isTrue();
    assertThat(
            ImmutableTransactionValidationParams.builder()
                .checkOnchainPermissions(false)
                .build()
                .checkOnchainPermissions())
        .isFalse();
  }

  @Test
  public void checkLocalPermissions() {
    assertThat(
            ImmutableTransactionValidationParams.builder()
                .checkLocalPermissions(true)
                .build()
                .checkLocalPermissions())
        .isTrue();
    assertThat(
            ImmutableTransactionValidationParams.builder()
                .checkLocalPermissions(false)
                .build()
                .checkLocalPermissions())
        .isFalse();
  }

  @Test
  public void transactionSimulator() {
    final TransactionValidationParams params = TransactionValidationParams.transactionSimulator();
    assertThat(params.isAllowFutureNonce()).isFalse();
    assertThat(params.checkOnchainPermissions()).isFalse();
    assertThat(params.checkLocalPermissions()).isFalse();
  }

  @Test
  public void processingBlock() {
    final TransactionValidationParams params = TransactionValidationParams.processingBlock();
    assertThat(params.isAllowFutureNonce()).isFalse();
    assertThat(params.checkOnchainPermissions()).isTrue();
    assertThat(params.checkLocalPermissions()).isFalse();
  }

  @Test
  public void transactionPool() {
    final TransactionValidationParams params = TransactionValidationParams.transactionPool();
    assertThat(params.isAllowFutureNonce()).isTrue();
    assertThat(params.checkOnchainPermissions()).isTrue();
    assertThat(params.checkLocalPermissions()).isTrue();
  }

  @Test
  public void mining() {
    final TransactionValidationParams params = TransactionValidationParams.mining();
    assertThat(params.isAllowFutureNonce()).isFalse();
    assertThat(params.checkOnchainPermissions()).isTrue();
    assertThat(params.checkLocalPermissions()).isTrue();
  }

  @Test
  public void blockReplay() {
    final TransactionValidationParams params = TransactionValidationParams.blockReplay();
    assertThat(params.isAllowFutureNonce()).isFalse();
    assertThat(params.checkOnchainPermissions()).isFalse();
    assertThat(params.checkLocalPermissions()).isFalse();
  }
}
