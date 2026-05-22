# Manual Spec Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional `test_specs` parameter to the `apim-4.7.0-ui-integration-staging-testgrid-pipeline` Jenkins job so a user can run only the specs they list; leaving it blank runs the full suite as today.

**Architecture:** A multi-line Jenkins `TextParameterDefinition` is normalized in `main.groovy` into a comma-separated string, passed as a new 5th positional argument through `test-deployment.sh` into the product repo's `test.sh`, which uses it to scope the Cypress first pass. The automatic rerun pass and flaky reporting are untouched.

**Tech Stack:** Jenkins declarative pipeline (Groovy), Bash, Cypress, `jenkins-cli.jar`.

**Note on testing:** This is CI pipeline code — there is no local test framework. "Verification" means running the actual Jenkins job. Each code task is verified by inspection + syntax check; end-to-end verification is Task 5.

**Repos & branches involved:**
- `testgrid-jenkins-library` — branch `test-4.7.0` (local: `/Users/isuru/WSO2/temp/testgrid-jenkins-library`)
- `support-apim-apps` — branch `support-9.3.194.x-full` (local: `/Users/isuru/WSO2/temp/support-apim-apps`)
- Jenkins job config XML — not in any repo; edited via `jenkins-cli.jar`.

Credentials are in `/Users/isuru/WSO2/temp/.env` (`JENKINS_URL`, `JENKINS_AUTH`).

---

### Task 1: Scope the Cypress first pass in `test.sh`

**Files:**
- Modify: `support-apim-apps/tests/test.sh` (branch `support-9.3.194.x-full`)

- [ ] **Step 1: Confirm the repo is on the right branch**

Run:
```bash
cd /Users/isuru/WSO2/temp/support-apim-apps && git rev-parse --abbrev-ref HEAD
```
Expected: `support-9.3.194.x-full`

- [ ] **Step 2: Initialize the `TEST_SPECS` variable**

In `tests/test.sh`, find the header block (lines 6-8):
```bash
HOME=`pwd`
TEST_SCRIPT=test.sh
MVNSTATE=1
```
Change it to:
```bash
HOME=`pwd`
TEST_SCRIPT=test.sh
MVNSTATE=1
TEST_SPECS=""
```

- [ ] **Step 3: Parse the `--test-specs` long option**

In `tests/test.sh`, find the `mvn-opts` case inside the `-)` branch of the getopts loop:
```bash
                mvn-opts)
                    val="${!OPTIND}"; OPTIND=$(( $OPTIND + 1 ))
                    MAVEN_OPTS=$val
                    ;;
```
Add a new case directly after it:
```bash
                mvn-opts)
                    val="${!OPTIND}"; OPTIND=$(( $OPTIND + 1 ))
                    MAVEN_OPTS=$val
                    ;;
                test-specs)
                    val="${!OPTIND}"; OPTIND=$(( $OPTIND + 1 ))
                    TEST_SPECS=$val
                    ;;
```

- [ ] **Step 4: Scope the first Cypress pass**

In `tests/test.sh`, find the first-pass invocation:
```bash
nohup Xvfb :99 > /dev/null 2>&1 &
export DISPLAY=:99
NO_COLOR=1 npm run test
MVNSTATE_FIRST=$?
pkill Xvfb || true
```
Change it to:
```bash
nohup Xvfb :99 > /dev/null 2>&1 &
export DISPLAY=:99
if [ -n "$TEST_SPECS" ]; then
    echo "===== test_specs set — running user-selected specs only ====="
    echo "$TEST_SPECS" | tr ',' '\n'
    NO_COLOR=1 npx cypress run --e2e --spec "$TEST_SPECS"
else
    NO_COLOR=1 npm run test
fi
MVNSTATE_FIRST=$?
pkill Xvfb || true
```

- [ ] **Step 5: Syntax-check the script**

Run:
```bash
bash -n /Users/isuru/WSO2/temp/support-apim-apps/tests/test.sh && echo "SYNTAX OK"
```
Expected: `SYNTAX OK`

- [ ] **Step 6: Commit**

```bash
cd /Users/isuru/WSO2/temp/support-apim-apps
git add tests/test.sh
git commit -m "Add --test-specs option to scope Cypress first pass

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 2: Forward the spec list through `test-deployment.sh`

**Files:**
- Modify: `testgrid-jenkins-library/scripts/test-deployment.sh` (branch `test-4.7.0`)

- [ ] **Step 1: Confirm the repo is on the right branch**

Run:
```bash
cd /Users/isuru/WSO2/temp/testgrid-jenkins-library && git rev-parse --abbrev-ref HEAD
```
Expected: `test-4.7.0`

- [ ] **Step 2: Read the new 5th positional argument**

In `scripts/test-deployment.sh`, find the argument block:
```bash
deploymentName=$1
productRepository=$2
productTestBranch=$3
productTestScript=$4
currentScript=$(dirname $(realpath "$0"))
```
Change it to:
```bash
deploymentName=$1
productRepository=$2
productTestBranch=$3
productTestScript=$4
testSpecs=$5
currentScript=$(dirname $(realpath "$0"))
```

- [ ] **Step 3: Forward `--test-specs` to `test.sh`**

In `scripts/test-deployment.sh`, find the `test.sh` invocation inside `deploymentTest()`:
```bash
    source ${productDirectoryLocation}/${productTestScript} --input-dir "${deploymentDirectory}"  --output-dir "${testOutputDir}"
```
Change it to:
```bash
    source ${productDirectoryLocation}/${productTestScript} --input-dir "${deploymentDirectory}"  --output-dir "${testOutputDir}" --test-specs "${testSpecs}"
```

- [ ] **Step 4: Syntax-check the script**

Run:
```bash
bash -n /Users/isuru/WSO2/temp/testgrid-jenkins-library/scripts/test-deployment.sh && echo "SYNTAX OK"
```
Expected: `SYNTAX OK`

- [ ] **Step 5: Commit**

```bash
cd /Users/isuru/WSO2/temp/testgrid-jenkins-library
git add scripts/test-deployment.sh
git commit -m "Forward test-specs argument to product test script

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 3: Normalize and pass the parameter in `main.groovy`

**Files:**
- Modify: `testgrid-jenkins-library/main.groovy` (branch `test-4.7.0`)

- [ ] **Step 1: Add the `testSpecs` script-level variable**

In `main.groovy`, find the top-level variable block (after the imports):
```groovy
def deploymentDirectories = []
def updateType = ""
def s3BucketName = "testgrid-pipeline-logs"
def s3BuildLogPath = ""
def s3PathConstructor = ""
```
Change it to:
```groovy
def deploymentDirectories = []
def updateType = ""
def s3BucketName = "testgrid-pipeline-logs"
def s3BuildLogPath = ""
def s3PathConstructor = ""
def testSpecs = ""
```

- [ ] **Step 2: Normalize the multi-line parameter**

In `main.groovy`, find the start of the *Constructing parameter files* stage `script` block:
```groovy
    stage('Constructing parameter files'){
        steps {
            script {
                withCredentials([string(credentialsId: 'AWS_ACCESS_KEY_ID', variable: 'accessKey'),
```
Insert the normalization line immediately after `script {`:
```groovy
    stage('Constructing parameter files'){
        steps {
            script {
                // Normalize the optional multi-line test_specs parameter into the
                // single comma-separated form Cypress --spec expects. Undeclared in
                // most jobs, so read via params (returns null) not a bare global.
                testSpecs = (params.test_specs ?: '').readLines()
                                .collect { it.trim() }
                                .findAll { it }
                                .join(',')
                withCredentials([string(credentialsId: 'AWS_ACCESS_KEY_ID', variable: 'accessKey'),
```

- [ ] **Step 3: Pass `testSpecs` as the 5th argument to `test-deployment.sh`**

In `main.groovy`, find the `test-deployment.sh` invocation inside `create_build_jobs`:
```groovy
                    try {
                        sh'''
                            ./scripts/test-deployment.sh '''+deploymentDirectory+''' ${product_repository} ${product_test_branch} ${product_test_script}
                        '''
                    } finally {
```
Change it to:
```groovy
                    try {
                        sh'''
                            ./scripts/test-deployment.sh '''+deploymentDirectory+''' ${product_repository} ${product_test_branch} ${product_test_script} "'''+testSpecs+'''"
                        '''
                    } finally {
```

- [ ] **Step 4: Verify the edits visually**

Run:
```bash
grep -n "testSpecs" /Users/isuru/WSO2/temp/testgrid-jenkins-library/main.groovy
```
Expected: three matches — the `def` declaration, the normalization assignment, and the `test-deployment.sh` invocation.

- [ ] **Step 5: Commit**

```bash
cd /Users/isuru/WSO2/temp/testgrid-jenkins-library
git add main.groovy
git commit -m "Normalize test_specs param and pass it to test-deployment.sh

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 4: Add the `test_specs` parameter to the Jenkins job

**Files:**
- No repo files. The Jenkins job config XML is fetched, edited, and pushed back via `jenkins-cli.jar`.

Job path: `U2/UI-Tests/product-apim/Staging/apim-4.7.0-ui-integration-staging-testgrid-pipeline`

- [ ] **Step 1: Back up the current job config**

Run:
```bash
cd /Users/isuru/WSO2/temp && set -a && source .env && set +a
java -jar jenkins-cli.jar -s "$JENKINS_URL" -auth "$JENKINS_AUTH" \
  get-job "U2/UI-Tests/product-apim/Staging/apim-4.7.0-ui-integration-staging-testgrid-pipeline" \
  > /tmp/ui-pipeline-job.xml
echo "Backed up $(wc -l < /tmp/ui-pipeline-job.xml) lines"
```
Expected: a line count is printed and `/tmp/ui-pipeline-job.xml` exists.

- [ ] **Step 2: Insert the new parameter definition**

Edit `/tmp/ui-pipeline-job.xml`. Find the closing of the `use_staging` parameter, which is the last parameter before `</parameterDefinitions>`:
```xml
        <hudson.model.BooleanParameterDefinition>
          <name>use_staging</name>
          <description>If testing environment is staging be true. If using UAT this should be false.</description>
          <defaultValue>true</defaultValue>
        </hudson.model.BooleanParameterDefinition>
      </parameterDefinitions>
```
Change it to (insert the new block before `</parameterDefinitions>`):
```xml
        <hudson.model.BooleanParameterDefinition>
          <name>use_staging</name>
          <description>If testing environment is staging be true. If using UAT this should be false.</description>
          <defaultValue>true</defaultValue>
        </hudson.model.BooleanParameterDefinition>
        <hudson.model.TextParameterDefinition>
          <name>test_specs</name>
          <description>OPTIONAL. Limit the run to specific Cypress specs. Enter one spec path or glob per line, relative to the tests/ directory (e.g. cypress/e2e/publisher/foo.cy.js or cypress/e2e/publisher/**/*.cy.js). Leave blank to run the full suite. A path that matches no spec file will fail the build.</description>
          <defaultValue></defaultValue>
          <trim>false</trim>
        </hudson.model.TextParameterDefinition>
      </parameterDefinitions>
```

- [ ] **Step 3: Validate the edited XML is well-formed**

Run:
```bash
xmllint --noout /tmp/ui-pipeline-job.xml && echo "XML OK"
```
Expected: `XML OK`

- [ ] **Step 4: Push the updated config to Jenkins**

Run:
```bash
cd /Users/isuru/WSO2/temp && set -a && source .env && set +a
java -jar jenkins-cli.jar -s "$JENKINS_URL" -auth "$JENKINS_AUTH" \
  update-job "U2/UI-Tests/product-apim/Staging/apim-4.7.0-ui-integration-staging-testgrid-pipeline" \
  < /tmp/ui-pipeline-job.xml
echo "update-job exit: $?"
```
Expected: `update-job exit: 0`

- [ ] **Step 5: Verify the parameter is registered**

Run:
```bash
cd /Users/isuru/WSO2/temp && set -a && source .env && set +a
java -jar jenkins-cli.jar -s "$JENKINS_URL" -auth "$JENKINS_AUTH" \
  get-job "U2/UI-Tests/product-apim/Staging/apim-4.7.0-ui-integration-staging-testgrid-pipeline" \
  | grep -A1 "TextParameterDefinition"
```
Expected: output shows `<name>test_specs</name>`.

---

### Task 5: Push branches and verify end-to-end

**Files:** none — this task pushes commits and runs Jenkins builds.

- [ ] **Step 1: Push `support-apim-apps`**

The Jenkins job clones `support-apim-apps` branch `support-9.3.194.x-full` at build time, so the Task 1 commit must be on the remote.

Run:
```bash
cd /Users/isuru/WSO2/temp/support-apim-apps
git push origin support-9.3.194.x-full
```
Expected: push succeeds.

- [ ] **Step 2: Push `testgrid-jenkins-library`**

The job loads `main.groovy` from branch `test-4.7.0`, so Tasks 2-3 commits must be on the remote.

Run:
```bash
cd /Users/isuru/WSO2/temp/testgrid-jenkins-library
git push origin test-4.7.0
```
Expected: push succeeds.

- [ ] **Step 3: Trigger a targeted build**

Pick one small, fast spec that exists in `support-apim-apps/tests/cypress/e2e/` (confirm a path first with `ls`). Trigger the job with `test_specs` set to that single path:

```bash
cd /Users/isuru/WSO2/temp && set -a && source .env && set +a
java -jar jenkins-cli.jar -s "$JENKINS_URL" -auth "$JENKINS_AUTH" \
  build "U2/UI-Tests/product-apim/Staging/apim-4.7.0-ui-integration-staging-testgrid-pipeline" \
  -p test_specs="cypress/e2e/<chosen-spec>.cy.js" -v -w
```
Expected: build starts and streams console output.

- [ ] **Step 4: Confirm only the selected spec ran**

In the build console output, confirm the `===== test_specs set — running user-selected specs only =====` line appears and that Cypress ran only the chosen spec (not the full suite). The build result should reflect that single spec's outcome.

- [ ] **Step 5: Trigger a full-suite regression build**

Trigger the job with `test_specs` left blank to confirm no regression:

```bash
cd /Users/isuru/WSO2/temp && set -a && source .env && set +a
java -jar jenkins-cli.jar -s "$JENKINS_URL" -auth "$JENKINS_AUTH" \
  build "U2/UI-Tests/product-apim/Staging/apim-4.7.0-ui-integration-staging-testgrid-pipeline" \
  -p test_specs="" -v -w
```
Expected: build runs the full Cypress suite (the `test_specs set` line does NOT appear; `npm run test` runs).

---

## Notes

- Tasks 1-3 are independent edits and can be done in any order, but all must be pushed (Task 5) before a verification build will pick them up.
- The `apim-apps` (`main` branch) copy of `test.sh` is intentionally **not** changed in this iteration — see the design spec's scope note.
- If a verification build fails for infrastructure reasons (AWS/CFN), that is unrelated to this change; re-check by inspecting whether the `test_specs` console line behaved correctly.
