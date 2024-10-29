#!/bin/bash
##
## Copyright contributors to Besu.
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

# Construct the command as a single string
COMMAND="/opt/besu/bin/besu $@"

# Check if current user is not root. If not, run the command as is.
if [ "$(id -u)" -ne 0 ]; then
    exec /bin/bash -c "$COMMAND"
fi

# Run Besu first to get paths needing permission adjustment
output=$(/opt/besu/bin/besu --print-paths-and-exit $BESU_USER_NAME "$@")

# Parse the output to find the paths and their required access types
echo "$output" | while IFS=: read -r prefix path accessType; do
    if [[ "$prefix" == "PERMISSION_CHECK_PATH" ]]; then
      # Change ownership to besu user and group
      chown -R $BESU_USER_NAME:$BESU_USER_NAME $path

      # Ensure read/write permissions for besu user

      echo "Setting permissions for: $path with access: $accessType"

      if [[ "$accessType" == "READ" ]]; then
        # Set read-only permissions for besu user
        # Add execute for directories to allow access
        find $path -type d -exec chmod u+rx {} \;
        find $path -type f -exec chmod u+r {} \;
      elif [[ "$accessType" == "READ_WRITE" ]]; then
        # Set read/write permissions for besu user
        # Add execute for directories to allow access
        find $path -type d -exec chmod u+rwx {} \;
        find $path -type f -exec chmod u+rw {} \;
      fi
    fi
done

# Switch to the besu user and execute the command
exec su -s /bin/bash "$BESU_USER_NAME" -c "$COMMAND"
