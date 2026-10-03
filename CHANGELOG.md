# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added

- Usage reporting: one `startup` event (name, version, `service=true`) is sent to the trace service on start-up; off with `USAGE_REPORTING_ENABLED=false` (Helm `usageReporting.enabled=false`), `TRACE_USAGE_REPORTING=off` or `DO_NOT_TRACK=1`

### Fixed

- `Dockerfile` now copies `target/k8s-ai-operator-*.jar`, the artifact `./mvnw clean package` actually produces; the previous `edgescaleai-tech-interview-*.jar` pattern matched nothing, so `docker build` failed
- README *Security Notes* no longer claims LLM responses are schema-validated; it describes what is enforced (single JSON object + verb allowlist)

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
