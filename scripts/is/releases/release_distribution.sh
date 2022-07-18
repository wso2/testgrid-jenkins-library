#!/bin/bash
# -------------------------------------------------------------------------------------
# Copyright (c) 2022 WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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
# --------------------------------------------------------------------------------------
ProductDistributionName='wso2is-6.0.0-alpha3-SNAPSHOT'
ProductVersion='wso2is-6.0.0'
ProductDistributionURL='https://wso2.org/jenkins/view/products/job/products/job/product-is/org.wso2.is$wso2is/lastSuccessfulBuild/artifact/org.wso2.is/wso2is/6.0.0-alpha3-SNAPSHOT/wso2is-6.0.0-alpha3-SNAPSHOT.zip'

wget -O /etc/puppet/code/environments/production/modules/installers/files/${ProductDistributionName}.zip ${ProductDistributionURL}
pushd /etc/puppet/code/environments/production/modules/installers/files/
md5sum ${ProductDistributionName}.zip
unzip -q ${ProductDistributionName}.zip
rm -r ${ProductVersion}.zip # remove if any existing packs
mv ${ProductDistributionName} ${ProductVersion}
zip -r ${ProductVersion}.zip ${ProductVersion}/
rm -rf ${ProductVersion}
rm ${ProductDistributionName}.zip
