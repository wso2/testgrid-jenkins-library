#!/bin/bash
# ------------------------------------------------------------------------
#
# Copyright (c) 2026, WSO2 Inc. (https://www.wso2.com). All Rights Reserved.
#
# This software is the property of WSO2 Inc. and its suppliers, if any.
# Dissemination of any information or reproduction of any material contained
# herein in any form is strictly forbidden, unless permitted by WSO2
# expressly. You may not alter or remove any copyright or other notice from
# copies of this content.
#
# ------------------------------------------------------------------------

# Script to update WSO2 deployment.toml with database configuration from infra.json
# Usage: ./update_db_config.sh <db_type>
# Example: ./update_db_config.sh mysql

# Check if a database type was provided
if [ $# -lt 1 ]; then
    echo "Usage: $0 <db_type>"
    echo "Available database types: $(jq -r '.jdbc[].name' infra.json | tr '\n' ', ' | sed 's/,$//')"
    exit 1
fi

DB_TYPE_INPUT=$1
WSO2_PRODUCT_VERSION=$2
DEPLOYMENT_TOML="$WSO2_PRODUCT_VERSION/repository/conf/deployment.toml"
INFRA_JSON="infra.json"
NEW_TOML="${DEPLOYMENT_TOML}.new"
BACKUP_TOML="${DEPLOYMENT_TOML}.bak"

# Check if files exist
if [ ! -f "$INFRA_JSON" ]; then
    echo "Error: $INFRA_JSON not found"
    exit 1
fi

if [ ! -f "$DEPLOYMENT_TOML" ]; then
    echo "Error: $DEPLOYMENT_TOML not found"
    exit 1
fi

DB_TYPE=$DB_TYPE_INPUT

# Check if the database type exists in infra.json

DB_EXISTS=$(jq -r --arg db "$DB_TYPE" '.jdbc[] | select(.name == $db) | .name' "$INFRA_JSON")
if [ -z "$DB_EXISTS" ]; then
    echo "Error: Database type '$DB_TYPE' (mapped from '$DB_TYPE_INPUT') not found in $INFRA_JSON"
    echo "Available database types: $(jq -r '.jdbc[].name' "$INFRA_JSON" | tr '\n' ', ' | sed 's/,$//')"
    exit 1
fi

echo "Updating $DEPLOYMENT_TOML with $DB_TYPE database configuration..."

# Create a backup
cp "$DEPLOYMENT_TOML" "$BACKUP_TOML"

# Extract AgentIdentity URL to check if it is configured in infra.json
AGENTIDENTITY_URL=$(jq -r --arg db "$DB_TYPE" '.jdbc[] | select(.name == $db) | .database[] | select(.name == "WSO2AGENTIDENTITY_DB") | .url' "$INFRA_JSON")

# Create new content
{
  # Read the file until we find identity_db section
  while IFS= read -r line; do
    if [[ "$line" =~ ^\[database.identity_db\] ]]; then
      # Write the identity_db section
      echo "[database.identity_db]"
      echo "type = \"\$env{IDENTITY_DATABASE_TYPE}\""
      echo "url = \"\$env{IDENTITY_DATABASE_URL}\""
      echo "username = \"\$env{IDENTITY_DATABASE_USERNAME}\""
      echo "password = \"\$env{IDENTITY_DATABASE_PASSWORD}\""
      echo "driver = \"\$env{IDENTITY_DATABASE_DRIVER}\""
      echo "validationQuery = \"\$env{IDENTITY_DATABASE_VALIDATION_QUERY}\""
      echo ""
      
      # Skip the original section
      while IFS= read -r section_line; do
        if [[ "$section_line" =~ ^\[ ]]; then
          # We reached a new section, process it
          if [[ "$section_line" =~ ^\[database.shared_db\] ]]; then
            # Write shared_db section
            echo "[database.shared_db]"
            echo "type = \"\$env{SHARED_DATABASE_TYPE}\""
            echo "url = \"\$env{SHARED_DATABASE_URL}\""
            echo "username = \"\$env{SHARED_DATABASE_USERNAME}\""
            echo "password = \"\$env{SHARED_DATABASE_PASSWORD}\""
            echo "driver = \"\$env{SHARED_DATABASE_DRIVER}\""
            echo "validationQuery = \"\$env{SHARED_DATABASE_VALIDATION_QUERY}\""
            echo ""
            
            # Skip the original shared_db section
            while IFS= read -r shared_section_line; do
              if [[ "$shared_section_line" =~ ^\[ ]]; then
                # We reached the next section after shared_db
                if [[ "$shared_section_line" =~ ^\[datasource.AgentIdentity\] ]]; then
                  # Write AgentIdentity datasource section if AGENTIDENTITY_DB exists
                  if [ -n "$AGENTIDENTITY_URL" ] && [ "$AGENTIDENTITY_URL" != "null" ]; then
                    echo "[datasource.AgentIdentity]"
                    echo "id = \"AgentIdentity\""
                    echo "type = \"\$env{AGENTIDENTITY_DATABASE_TYPE}\""
                    echo "url = \"\$env{AGENTIDENTITY_DATABASE_URL}\""
                    echo "username = \"\$env{AGENTIDENTITY_DATABASE_USERNAME}\""
                    echo "password = \"\$env{AGENTIDENTITY_DATABASE_PASSWORD}\""
                    echo "driver = \"\$env{AGENTIDENTITY_DATABASE_DRIVER}\""
                    echo "validationQuery = \"\$env{AGENTIDENTITY_DATABASE_VALIDATION_QUERY}\""
                    echo ""
                    
                    # Skip the original AgentIdentity section
                    while IFS= read -r agent_section_line; do
                      if [[ "$agent_section_line" =~ ^\[ ]]; then
                        # We reached the next section after AgentIdentity
                        echo "$agent_section_line"
                        break
                      fi
                    done
                  else
                    # If no AGENTIDENTITY_DB in infra.json, keep the original section
                    echo "$shared_section_line"
                  fi
                else
                  echo "$shared_section_line"
                fi
                break
              fi
            done
          else
            # This is not the shared_db section, just echo it
            echo "$section_line"
          fi
          break
        fi
      done
    else
      echo "$line"
    fi
  done
} < "$BACKUP_TOML" > "$NEW_TOML"

# Replace original file with our new file
mv "$NEW_TOML" "$DEPLOYMENT_TOML"

echo "Database configuration updated successfully for '$DB_TYPE_INPUT' (mapped to '$DB_TYPE_DISPLAY')"

# Show the updated sections
echo -e "\nUpdated database configuration in $DEPLOYMENT_TOML:"
grep -A 6 "^\[database\." "$DEPLOYMENT_TOML"
