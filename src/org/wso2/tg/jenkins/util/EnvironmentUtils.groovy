package org.wso2.tg.jenkins.util

import hudson.AbortException
import org.wso2.tg.jenkins.PipelineContext


class EnvironmentUtils implements Serializable {

    static String getEnvironmentVariable(variable) {
        try {
            def ctx = PipelineContext.getContext()
            def envVar = ctx.sh returnStdout: true, script: """#!/bin/bash --login
                                                                echo \$$variable"""
            return envVar.trim()
        } catch (AbortException e) {
            throw new AbortException("Error while retrieving the environment variable '$variable'. Reason: $e.message.")
        }
    }
}
