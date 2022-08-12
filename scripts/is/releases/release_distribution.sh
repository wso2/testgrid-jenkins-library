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
# Please Note that this is only for packs that are yet to be released in GA (testing with product distributions)
Product='wso2is'
ProductVersion='6.0.0'
ProductDistribution='rc2'
snapshot=0 # make it to 1 if you are testing for a snapshot version

if [ $snapshot -eq 0  ]
then
  # For released versions downloading from github
  ProductDistributionURL='https://github.com/wso2/product-is/releases/download/v'${ProductVersion}'-'${ProductDistribution}'/'${Product}'-'${ProductVersion}'-'${ProductDistribution}'.zip'
else
  # For snapshots-downloading from jenkins
  ProductDistributionURL='https://wso2.org/jenkins/view/products/job/products/job/product-is/org.wso2.is$wso2is/lastSuccessfulBuild/artifact/org.wso2.is/'${Product}'/'${ProductVersion}'-'${ProductDistribution}'/'${Product}'-'${ProductVersion}'-'${ProductDistribution}'.zip'
fi
wget -O /etc/puppet/code/environments/production/modules/installers/files/${Product}-${ProductVersion}-${ProductDistribution}.zip ${ProductDistributionURL}
pushd /etc/puppet/code/environments/production/modules/installers/files/
md5sum ${Product}-${ProductVersion}-${ProductDistribution}.zip
unzip -q ${Product}-${ProductVersion}-${ProductDistribution}.zip
rm -r ${Product}-${ProductVersion}.zip # remove if any existing packs
mv ${Product}-${ProductVersion}-${ProductDistribution}  ${Product}-${ProductVersion}
zip -r  ${Product}-${ProductVersion}.zip  ${Product}-${ProductVersion}/
rm -rf  ${Product}-${ProductVersion}
rm ${Product}-${ProductVersion}-${ProductDistribution}.zip
