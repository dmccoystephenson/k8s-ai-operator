# Commands Reference — k8s-ai-operator

This service exposes a single REST endpoint. "Commands" are natural-language prompts sent to that endpoint, interpreted by AWS Bedrock (Claude) and translated into Kubernetes operations.

## API Commands

### POST /k8s/execute

**Description:** Accepts a natural-language prompt and executes the corresponding Kubernetes command if it is permitted by the verb allowlist.  
**Permission:** Public by default. If you configure an IAM authorizer (for example, via the SAM template `Auth` settings), the endpoint will then require valid AWS IAM credentials with invoke access.  
**Usage:** `POST /k8s/execute`  
**Content-Type:** `application/json`

**Request body:**
```json
{
  "request_id": "<string>",
  "user_prompt": "<natural language description of the desired operation>"
}
```

**Success Response — `200 OK`:**
```json
{
  "request_id": "<string>",
  "command": {
    "verb": "get | apply",
    "resource": "pods | deployments | services",
    "namespace": "<string>"
  },
  "result": "<kubectl output>",
  "allowed": true
}
```

**Blocked Response — `400 Bad Request`:**
```json
{
  "request_id": "<string>",
  "allowed": false,
  "reason": "Verb '<verb>' is not permitted"
}
```

## Allowed Verbs

| Verb | Description |
|------|-------------|
| `get` | List or describe Kubernetes resources |
| `apply` | Apply a Kubernetes manifest |

## Blocked Verbs

The following verbs are hard-blocked at the service layer and cannot be invoked regardless of prompt wording:

| Verb | Reason blocked |
|------|---------------|
| `delete` | Destructive operation |
| `exec` | Arbitrary code execution risk |
| `scale` | Can reduce availability to zero |
| `patch` | Unrestricted resource modification |

## Supported Resources

| Resource | Description |
|----------|-------------|
| `pods` | Kubernetes Pod resources |
| `deployments` | Kubernetes Deployment resources |
| `services` | Kubernetes Service resources |

## Example Prompts

| Prompt | Parsed command |
|--------|---------------|
| "Show me the pods in namespace production" | `get pods -n production` |
| "List all deployments in staging" | `get deployments -n staging` |
| "What services are running in production?" | `get services -n production` |
| "Apply this deployment YAML to staging" | `apply -n staging` |
