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

/**
 * Populates the TestGrid yaml input parameters in infra,deployment,scenario sections
 * according to the config properties file definitions
 *
 * Ex :
 * common.xxx=yyyy (insert the input parameter to all three sections)
 * infra.default.xxx=yyy (insert to input parameter section of infra section by default)
 * infra.xxx=yyy (insert to input parameter section of infra section only if user has specified in the
 * testgridProvidedParameters section
 *
 * @param tgYamlContent parsed testgrid yaml file content
 * @param commonConfigs parsed common-config  properties file content
 * @return testgrid yaml file content with input parameters correctly set
 */
def addCommonConfigsToTestGridYaml(tgYamlContent, commonConfigs) {

    echo "Populating testgrid yaml file with common configs"

    commonConfigs.each { key, value ->
        def infraProvisioners = null;
        def deploymentPatterns = null;
        def scenarioConfigs = null;

        if (tgYamlContent.infrastructureConfig !=null ){
            infraProvisioners = tgYamlContent.infrastructureConfig.provisioners
        }
        if(tgYamlContent.deploymentConfig!=null){
            deploymentPatterns = tgYamlContent.deploymentConfig.deploymentPatterns
        }

        scenarioConfigs = tgYamlContent.scenarioConfigs
        //split each property from . symbol
        def split = key.split('\\.');
        if (split[0].equals("common")) {
            //apply to all  the available inputParams
            addToInputParams(infraProvisioners, split[split.length - 1], value,"infra")
            addToInputParams(deploymentPatterns, split[split.length - 1], value,"deployment")
            //handle scenario
            if(scenarioConfigs.inputParameters.get(0) != null ){
                scenarioConfigs.inputParameters.get(0).put(split[split.length - 1], value)
            }else{
                def paramvalues = new LinkedHashMap<String,String>()
                paramvalues.put(split[split.length - 1], value)
                scenarioConfigs.get(0).put("inputParameters",paramvalues)
            }

        } else if (split[0].equals("infra")) { //apply to only infraConfig section
            //proceed if only valid entry
            if (split.length >= 2 && infraProvisioners != null) {

                //add to inputParams if default prefix is present
                if (split[1].equals("default")) {
                    addToInputParams(infraProvisioners, split[split.length - 1], value,"infra")
                } else {
                    if (infraProvisioners != null) {
                        infraProvisioners.each { provisioner ->
                            provisioner.scripts.each { script ->
                                if (script.testgridProvidedParameters != null) {
                                    script.testgridProvidedParameters.each { param ->
                                        if (param.equals(split[split.length - 1])) {
                                            if(script.inputParameters != null){
                                                script.inputParameters.put(split[split.length - 1], value)
                                                echo "Added new infra input Parameter " + key
                                            }else{
                                                script.inputParameters = new LinkedHashMap<String,String>();
                                                script.inputParameters.put(split[split.length - 1], value)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        } else if (split[0].equals("deployment")) {
            if (split.length >= 2 && deploymentPatterns != null) {

                if (split[1].equals("default")) {
                    addToInputParams(deploymentPatterns, split[split.length - 1], value,"deployment")
                } else {
                    if (deploymentPatterns != null) {
                        deploymentPatterns.each { pattern ->
                            pattern.scripts.each { script ->
                                if (script.testgridProvidedParameters != null) {
                                    script.testgridProvidedParameters.each { param ->
                                        if (param.equals(split[split.length - 1])) {
                                            if(script.inputParameters!= null){
                                                script.inputParameters.put(split[split.length - 1], value)
                                            }else{
                                                script.inputParameters = new LinkedHashMap<String,String>();
                                                script.inputParameters.put(split[split.length - 1], value)
                                            }
                                            echo "Added new deployment input Parameter " + key
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        } else if (split[0].equals("test")) {
            if (split.length >= 2 && scenarioConfigs != null) {
                if (split[1].equals("default")) {
                    if (scenarioConfigs.inputParameters.get(0) != null) {
                        scenarioConfigs.inputParameters.get(0).put(split[split.length - 1], value)
                    }else{
                        def paramvalues = new LinkedHashMap<String,String>()
                        paramvalues.put(split[split.length - 1], value)
                        scenarioConfigs.get(0).put("infraParameters",paramvalues)
                    }
                    echo "Added new scenario input Parameter " + key
                } else {
                    if (scenarioConfigs.testgridProvidedParameters != null) {
                        scenarioConfigs.testgridProvidedParameters.each { paramList ->
                            paramList.each { param ->
                                if (param.equals(split[split.length - 1])) {
                                    if(scenarioConfigs.inputParameters.get(0) !=null){
                                        scenarioConfigs.inputParameters.get(0).put(split[split.length - 1], value)
                                    }else{
                                        def paramvalues = new LinkedHashMap<String,String>()
                                        paramvalues.put(split[split.length - 1], value)
                                        scenarioConfigs.get(0).put("inputParameters",paramvalues)
                                    }
                                    echo "Added new scenario input Parameter " + key
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    return tgYamlContent
}

/**
 * Helper method to add vales to inputParameters section of testgrid yaml file
 *
 * @param yamlContent section to add the new parameter
 * @param key key value of the new parameter
 * @param value value of new parameter
 */
def addToInputParams(yamlContent, key, value, section) {

    if (yamlContent != null) {
        yamlContent.each { content ->
            content.scripts.each { script ->
                if(script.inputParameters != null) {
                    script.inputParameters.put(key, value)
                }else{
                    script.inputParameters = new LinkedHashMap<String,String>();
                    script.inputParameters.put(key, value)
                }
                echo "Added new "+section+" input Parameter " + key
            }
        }
    }
}

