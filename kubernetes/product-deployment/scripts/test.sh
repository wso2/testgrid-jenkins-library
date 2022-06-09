#!/bin/bash
productName=$1;
relativeFilePathTestFile=$2
sh ./kubernetes/product-deployment/scripts/"$productName"/"test-$productName"/"$relativeFilePathTestFile"