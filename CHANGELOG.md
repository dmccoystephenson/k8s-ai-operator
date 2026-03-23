# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

## [0.0.1] — Initial release

### Added

- Spring Boot application with AWS Lambda Web Adapter support
- `POST /k8s/execute` endpoint accepting natural-language prompts
- AWS Bedrock (Claude Sonnet) integration for prompt-to-command translation
- Hard verb allowlist enforced at the service layer (`get`, `apply`)
- Hard verb blocklist (`delete`, `exec`, `scale`, `patch`)
- DynamoDB audit log (`K8sAgentExecutions`) for every request
- CloudWatch custom metrics (`AllowedCommands`, `BlockedCommands`, `ExecutionLatencyMs`)
- AWS SAM deployment template (`template.yaml`)
- EC2 provisioning script (`setup-ec2.sh`)
- EKS cluster setup script (`setup-eks.sh`)
- Docker-based container image (`Dockerfile`)
- Unit tests: `VerbGuardTest`, `K8sExecuteControllerTest`, `BedrockCommandParserTest`
