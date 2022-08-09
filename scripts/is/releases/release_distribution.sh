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
Product='wso2is'
ProductVersion='6.0.0'
ProductDistribution='' #Make it empty;i.e('') for rc1,rc2. Else keep values; i.e like ('alpha1')

if [ -z "$ProductDistribution" ]
then
  ProductDistributionURL='https://wso2.org/jenkins/view/products/job/products/job/product-is/lastSuccessfulBuild/artifact/modules/distribution/target/'${Product}'-'${ProductVersion}'.zip'
  wget -O /etc/puppet/code/environments/production/modules/installers/files/${Product}-${ProductVersion}.zip ${ProductDistributionURL}
  pushd /etc/puppet/code/environments/production/modules/installers/files/
  md5sum ${Product}-${ProductVersion}.zip
else
  ProductDistributionURL='https://wso2.org/jenkins/view/products/job/products/job/product-is/org.wso2.is$wso2is/lastSuccessfulBuild/artifact/org.wso2.is/'${Product}'/'${ProductVersion}'-'${ProductDistribution}'/'${Product}'-'${ProductVersion}'-'${ProductDistribution}'.zip'
  wget -O /etc/puppet/code/environments/production/modules/installers/files/${Product}-${ProductVersion}-${ProductDistribution}.zip ${ProductDistributionURL}
  pushd /etc/puppet/code/environments/production/modules/installers/files/
  md5sum ${Product}-${ProductVersion}-${ProductDistribution}.zip
  unzip -q ${Product}-${ProductVersion}-${ProductDistribution}.zip
  rm -r ${Product}-${ProductVersion}.zip # remove if any existing packs
  mv ${Product}-${ProductVersion}-${ProductDistribution}  ${Product}-${ProductVersion}
  zip -r  ${Product}-${ProductVersion}.zip  ${Product}-${ProductVersion}/
  rm -rf  ${Product}-${ProductVersion}
  rm ${Product}-${ProductVersion}-${ProductDistribution}.zip
fi
