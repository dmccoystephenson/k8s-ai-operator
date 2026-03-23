# User Guide — k8s-ai-operator

> For a full step-by-step deployment walkthrough, see [docs/user-guide.md](docs/user-guide.md).

## Prerequisites

### Tools

| Tool | How to get it | Verify |
|------|---------------|--------|
| AWS CLI v2 | https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html | `aws --version` |
| Java 21 | https://adoptium.net/ | `java -version` |
| Maven 3.9+ | https://maven.apache.org/download.cgi | `mvn -version` |
| AWS SAM CLI | https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html | `sam --version` |

### AWS Account Requirements

- An IAM user or role with permissions to create Lambda, API Gateway, DynamoDB, CloudWatch, IAM, and Bedrock resources
- **Amazon Bedrock model access enabled** for `anthropic.claude-3-sonnet-20240229-v1:0` in your target region

## First Steps

1. Build the project: `./mvnw clean package`
2. Deploy to AWS: `sam build && sam deploy --guided`
3. Note the API Gateway endpoint printed at the end of `sam deploy`

## Common Scenarios

### Ask for pods in a namespace

```bash
curl -s -X POST https://<ENDPOINT>/k8s/execute \
  -H "Content-Type: application/json" \
  -d '{
    "request_id": "req-001",
    "user_prompt": "Show me the pods in namespace production"
  }'
```

### Apply a manifest

```bash
curl -s -X POST https://<ENDPOINT>/k8s/execute \
  -H "Content-Type: application/json" \
  -d '{
    "request_id": "req-002",
    "user_prompt": "Apply this deployment YAML to the staging namespace"
  }'
```

### Blocked command example

```bash
curl -s -X POST https://<ENDPOINT>/k8s/execute \
  -H "Content-Type: application/json" \
  -d '{
    "request_id": "req-003",
    "user_prompt": "Delete all pods in production"
  }'
```

Response (`400 Bad Request`):
```json
{
  "request_id": "req-003",
  "allowed": false,
  "reason": "Verb 'delete' is not permitted"
}
```

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `k8s.execute` | Unauthenticated by default* | Allows sending commands via the API |

\* The `/k8s/execute` endpoint is deployed without authentication by default. To restrict access, configure an API Gateway authorizer (e.g., IAM, Cognito, or a custom Lambda authorizer) in the SAM template or via the AWS Console.

## Allowed vs Blocked Commands

| ✅ Allowed | ❌ Blocked |
|------------|-----------|
| Show me the pods in production | Delete all pods in production |
| List deployments in staging | Scale the deployment to zero |
| What services are in default? | Exec into the nginx container |
| Apply this manifest to staging | Patch the deployment |
