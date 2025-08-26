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
package org.hyperledger.besu.evm.frame;

/** The interface Soft Failure reason. */
public interface SoftFailureReason {

    /** The constant NONE. */
    SoftFailureReason NONE = DefaultSelfFailureReason.NONE;

    /** The constant INSUFFICIENT_BALANCE */
    SoftFailureReason INSUFFICIENT_BALANCE = DefaultSelfFailureReason.INSUFFICIENT_BALANCE;

    /** The constant MAX_CALL_DEPTH */
    SoftFailureReason MAX_CALL_DEPTH = DefaultSelfFailureReason.MAX_CALL_DEPTH;

    /** The constant MAX_BLOCK_ARG_SIZE */
    SoftFailureReason MAX_BLOCK_ARG_SIZE = DefaultSelfFailureReason.MAX_BLOCK_ARG_SIZE;

    /**
     * Name string.
     *
     * @return the string
     */
    String name();

    /**
     * Gets description.
     *
     * @return the description
     */
    String getDescription();

    /** The enum Default self failure reasons. */
    enum DefaultSelfFailureReason implements SoftFailureReason {
        /** None default soft failure reason. */
        NONE(""),
        /** Soft failure due to insufficient balance for transfer */
        INSUFFICIENT_BALANCE("insufficient balance for transfer"),
        /** Soft failure due to max call depth */
        MAX_CALL_DEPTH("max call depth exceeded"),
        /** Soft failure due to max block argument size */
        MAX_BLOCK_ARG_SIZE("max block argument size exceeded");

        /** The Description of soft failure. */
        final String description;

        /**
         * Instantiate DefaultSelfFailureReason with a description
         *
         * @param description The description to use
         */
        DefaultSelfFailureReason(final String description) {
            this.description = description;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }
}
