#!/bin/bash

# ----------------------------------------------------------------------------
#
# Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
#
# WSO2 Inc. licenses this file to you under the Apache License,
# Version 2.0 (the "License"); you may not use this file except
# in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
# ----------------------------------------------------------------------------

readonly WSO2_USERNAME=$1
readonly WSO2_PASSWORD=$2
readonly WSO2_PRODUCT=$3

# Standalone update tool. Newer packs (4.7.0+) no longer bundle wso2update_linux,
# so it is downloaded from S3 when missing. On older packs that still ship it this
# download is skipped (copy-if-missing), keeping the script version-agnostic.
# Path matches docker-builder.groovy's proven source (s3://<bucket>/testgrid-intg/).
readonly UPDATE_TOOL_S3_URI="s3://wum-for-testgrid/testgrid-intg/wso2update_linux"
readonly BACKUP_DIR="/opt/testgrid/workspace/backup"

echo "Unzipping $WSO2_PRODUCT Pack."
unzip -o -q $WSO2_PRODUCT.zip && cd $WSO2_PRODUCT/bin

PRODUCT_NAME=$(echo $WSO2_PRODUCT | rev | cut -d"-" -f2-  | rev)
PRODUCT_VERSION=$(echo $WSO2_PRODUCT | rev | cut -d"-" -f1  | rev)

if [ -z "${WSO2_USERNAME}" ] && [ -z "${WSO2_PASSWORD}" ]; then
  echo "WSO2 Credentials are empty. Proceeding with ${WSO2_PRODUCT} vanilla pack."
  exit 0
fi

# Ensure the update tool is available in the pack's bin/ directory.
if [ ! -f wso2update_linux ]; then
  echo "wso2update_linux not bundled in pack. Downloading from ${UPDATE_TOOL_S3_URI}."
  aws s3 cp "${UPDATE_TOOL_S3_URI}" wso2update_linux
  if [ $? -ne 0 ]; then
    echo "ERROR: Failed to download wso2update_linux from ${UPDATE_TOOL_S3_URI}."
    exit 1
  fi
else
  echo "wso2update_linux found in pack bin/."
fi

sudo chmod 755 wso2update_linux

# Warm-up runs: let the tool self-update and apply any baseline updates before
# switching to the staging (TESTING) channel.
sudo -E ./wso2update_linux --username "$WSO2_USERNAME" --password "$WSO2_PASSWORD" --backup "$BACKUP_DIR" -v
sudo -E ./wso2update_linux --username "$WSO2_USERNAME" --password "$WSO2_PASSWORD" --backup "$BACKUP_DIR" -v

echo "Applying staging (TESTING channel) updates."
export WSO2_UPDATES_UPDATE_LEVEL_STATE=TESTING

sudo -E ./wso2update_linux --username "$WSO2_USERNAME" --password "$WSO2_PASSWORD" --backup "$BACKUP_DIR" -v
update_exit_code=$?

# exit-code(2) => the tool self-updated; retry once (matches the docker-builder guarded flow).
if [ $update_exit_code -eq 2 ]; then
  echo "exit-code(2): Self update. Retrying update."
  sudo -E ./wso2update_linux --username "$WSO2_USERNAME" --password "$WSO2_PASSWORD" --backup "$BACKUP_DIR" -v
  update_exit_code=$?
fi

case $update_exit_code in
  0)
    echo "Successfully updated."
    cd ../../
    #rm -rf $WSO2_PRODUCT.zip
    #zip -r -q $WSO2_PRODUCT.zip $WSO2_PRODUCT
    exit 0
    ;;
  1)
    echo "exit-code(1): Default error."
    ;;
  3)
    echo "exit-code(3): Conflict(s) encountered."
    ;;
  4)
    echo "exit-code(4): Reverted."
    ;;
  *)
    echo "Unknown exit code ($update_exit_code) from update tool."
    ;;
esac
exit 1
