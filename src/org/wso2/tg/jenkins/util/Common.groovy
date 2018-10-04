/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.tg.jenkins.util

def getTimestamp(Date date = new Date()) {
    return date.format('yyyyMMddHHmmss', TimeZone.getTimeZone('GMT')) as String
}

def truncateTestRunLog(parallelNumber) {
    sh """
    if [ -d "${TESTGRID_HOME}/jobs/${PRODUCT}/${parallelNumber}/builds" ]; then
        cd ${TESTGRID_HOME}/jobs/${PRODUCT}/${parallelNumber}
      for file in builds/*/test-run.log ; do
        truncatedFile=\$(dirname \$file)/truncated-\$(basename \$file);
        head -n 10 \$file > \$truncatedFile;
        printf "......\n.....\n..(Skipping logs)..\n.....\n......\n" >> \$truncatedFile;
        grep -B 25 -A 25 -a "Reactor Summary" \$file >> \$truncatedFile || true;
        printf "......\n.....\n..(Skipping logs)..\n.....\n......\n" >> \$truncatedFile;
        tail -n 50 \$file >> \$truncatedFile;
      done
    else
        echo no logs found to truncate in ${TESTGRID_HOME}/jobs/${PRODUCT}/${parallelNumber}/builds!
    fi
   """
}

def getParameters(file) {
    def tpyaml = readFile(file)
    def m = tpyaml =~ /(parameters:)([A-z \n:'0-9\.-]*)(provisioners)/
    // echo tpyaml
    def params = m[0][2].trim().split('\n')
    // echo Long.toString(params.size())
    def name = ""
    params = params.sort()
    for (String s : params) {
        name += s.split(":")[1]
    }
    //echo "This is the name" + name
    return name
}

def getTestPlanId(file) {
    echo "This is the file " + file.toString()
     def tpyaml = readFile(file)
     def m = tpyaml =~ /(id:)([A-z :'0-9\.-]*)(\n)/
    return m[0][2].trim()
 }
