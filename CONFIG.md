# Configuration Guide — k8s-ai-operator

All configuration is defined in `src/main/resources/application.yml`. Environment variable overrides are supported for every AWS-specific option.

## spring.application.name

**Type:** string  
**Default:** `k8s-ai-operator`  
**Description:** The Spring application name, used in logs and actuator endpoints.

## management.endpoints.web.exposure.include

**Type:** string (comma-separated list)  
**Default:** `health`  
**Description:** Which Spring Actuator endpoints are exposed over HTTP. Only the `health` endpoint is enabled by default.

## aws.region

**Type:** string  
**Default:** `us-east-1`  
**Environment variable:** `AWS_REGION`  
**Description:** The AWS region where all AWS services (Bedrock, DynamoDB, CloudWatch) are accessed.

**Example:**
```yaml
aws:
  region: us-east-2
```

## aws.bedrock.model-id

**Type:** string  
**Default:** `anthropic.claude-3-sonnet-20240229-v1:0`  
**Environment variable:** `BEDROCK_MODEL_ID`  
**Description:** The Amazon Bedrock model identifier used to translate natural-language prompts into structured Kubernetes commands. The model must have access enabled in the AWS Console (Bedrock → Model access).

**Example:**
```yaml
aws:
  bedrock:
    model-id: anthropic.claude-3-sonnet-20240229-v1:0
```

## aws.bedrock.max-tokens

**Type:** integer  
**Default:** `512`  
**Environment variable:** `BEDROCK_MAX_TOKENS`  
**Description:** Maximum number of tokens the model may return per request. Limiting this caps cost and prevents the model from returning verbose multi-command responses.

**Example:**
```yaml
aws:
  bedrock:
    max-tokens: 512
```

## aws.dynamodb.table-name

**Type:** string  
**Default:** `K8sAgentExecutions`  
**Environment variable:** `DYNAMODB_TABLE`  
**Description:** The DynamoDB table used to store the audit log for every request (allowed and blocked). The table must exist before the application starts; it is not created automatically.

**Example:**
```yaml
aws:
  dynamodb:
    table-name: K8sAgentExecutions
```

## aws.cloudwatch.namespace

**Type:** string  
**Default:** `K8sAiOperator/Execution`  
**Environment variable:** `CLOUDWATCH_NAMESPACE`  
**Description:** The CloudWatch custom namespace under which `AllowedCommands`, `BlockedCommands`, and `ExecutionLatencyMs` metrics are published.

**Example:**
```yaml
aws:
  cloudwatch:
    namespace: K8sAiOperator/Execution
```

## k8s.allowed-verbs

**Type:** string (comma-separated list)  
**Default:** `get,apply`  
**Description:** Kubernetes verbs that the service will execute. Any verb produced by the model that is not in this list is rejected before execution.

**Example:**
```yaml
k8s:
  allowed-verbs: get,apply
```

## k8s.disallowed-verbs

**Type:** string (comma-separated list)  
**Default:** `delete,exec,scale,patch`  
**Description:** Kubernetes verbs that are explicitly blocked. These verbs are rejected at the service layer even if the model produces them. This is a code-layer control, not a prompt-layer control.

**Example:**
```yaml
k8s:
  disallowed-verbs: delete,exec,scale,patch
```
