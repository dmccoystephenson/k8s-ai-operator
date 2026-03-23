# Copilot Instructions

This repository follows the DPC (Dans Plugins Community) conventions defined at
https://github.com/Dans-Plugins/dpc-conventions. Read those conventions before
making any changes.

## Technology Stack

- Language: Java 21
- Build tool: Maven (Maven Wrapper — `./mvnw`)
- Framework: Spring Boot 3
- Cloud platform: AWS (Lambda, API Gateway, Bedrock, DynamoDB, CloudWatch)
- Deployment: AWS SAM (`template.yaml`)
- Test framework: JUnit 5

## Project Structure

- `src/main/java/com/stephenson/k8saioperator/` – Application source code
  - `controller/` – REST controllers (`K8sExecuteController`)
  - `service/` – Business logic (`BedrockCommandParser`, `VerbGuard`, `K8sClientAdapter`, `AuditService`)
  - `metrics/` – CloudWatch metrics emitter
  - `model/` – Request/response models
- `src/main/resources/application.yml` – Application configuration
- `src/test/java/` – Unit tests
- `template.yaml` – AWS SAM deployment template
- `docs/` – Extended deployment guides (EC2, EKS, deploy walkthrough, user guide)

## Coding Conventions

- Follow the existing package structure (`com.stephenson.k8saioperator`) when adding new classes.
- The verb allowlist is enforced in `VerbGuard.java` — do not relax or bypass it.
- All AWS service interactions go through dedicated service classes (`BedrockCommandParser`, `AuditService`, etc.).
- User prompts must never be written to logs or persisted to DynamoDB.
- Configuration values are read from `application.yml` with environment variable overrides.

## Contribution Workflow

- Branch from `develop` for all changes.
- Open a pull request against `develop`, not `main`.
- Reference the related GitHub issue in every pull request description.
