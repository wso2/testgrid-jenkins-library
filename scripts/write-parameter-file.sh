#!/bin/bash
# -------------------------------------------------------------------------------------
#
# Copyright (c) 2022, WSO2 Inc. (http://www.wso2.com). All Rights Reserved.
#
# This software is the property of WSO2 Inc. and its suppliers, if any.
# Dissemination of any information or reproduction of any material contained
# herein in any form is strictly forbidden, unless permitted by WSO2 expressly.
# You may not alter or remove any copyright or other notice from copies of this content.
#
# --------------------------------------------------------------------------------------

required_variables=3

if [ $# -ne ${required_variables} ]; then
    log_error "${required_variables} variables required"
    exit 0
else
    parameter_variable=${1}
    parameter_value=${2}
    parameter_file_path="${3}"
    contents="$(jq --arg parameter_variable "$parameter_variable" \
    --arg parameter_value "$parameter_value" \
    '.Parameters[$parameter_variable] = $parameter_value' $parameter_file_path)" && \
    echo "${contents}" > $parameter_file_path
fi
