#!/bin/bash
##
## Copyright contributors to Hyperledger Besu.
##
## Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
## the License. You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
## an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
## specific language governing permissions and limitations under the License.
##
## SPDX-License-Identifier: Apache-2.0
##


# Run Besu first to get paths needing permission adjustment
output=$(/opt/besu/bin/besu --print-paths-and-exit "$@")

# Parse the output to find the paths and their required access types
echo "$output" | while IFS=: read -r prefix path accessType; do
    if [[ "$prefix" == "PERMISSION_CHECK_PATH" ]]; then
      # Change ownership to besu user and group
      chown -R besu:besu $path

      # Ensure read/write permissions for besu user

      echo "Setting permissions for: $path with access: $accessType"

      if [[ "$accessType" == "READ" ]]; then
        # Set read-only permissions for besu user
        chmod -R u+r $path
      elif [[ "$accessType" == "READ_WRITE" ]]; then
        # Set read/write permissions for besu user
        chmod -R u+rw $path
      fi
    fi
done

# Finally, run Besu with the actual arguments passed to the container
# Construct the command as a single string
COMMAND="/opt/besu/bin/besu $@"

# Switch to the besu user and execute the command
exec su -s /bin/bash besu -c "$COMMAND"
