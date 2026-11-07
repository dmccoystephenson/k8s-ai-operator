# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added

- Usage reporting: one `startup` event (name, version, `service=true`) is sent to the trace service on start-up; off with `USAGE_REPORTING_ENABLED=false` (Helm `usageReporting.enabled=false`), `TRACE_USAGE_REPORTING=off` or `DO_NOT_TRACK=1`

### Fixed

- `k8s.disallowed-verbs` now takes precedence over `k8s.allowed-verbs`: a verb listed in both is rejected instead of executed. Configured verbs are trimmed and lowercased, so `get, apply` allows `apply` and `delete, EXEC` blocks `exec`
- `POST /k8s/execute` no longer reports a command that was executed as blocked when the audit or metrics step afterwards fails. It now returns `500` with `allowed: true`, the command output and a `reason`, and does not call `recordBlocked` or count a blocked command
- `POST /k8s/execute` no longer re-audits a blocked request with the audit/metrics error as its reason, or counts it as blocked twice, when recording the blocked request throws. That failure is now logged and the `400` keeps its `ExecuteResponse` shape and original `reason`, instead of escaping as Spring's default `500` body. As on the allowed path, the bundled audit and metrics classes already swallow their own write failures, so this covers an exception that escapes them
- The usage-reporting "Details" link (startup notice, docs and config comments) now points at https://danielstephenson.dev/usage-reporting, a public page, instead of a link into a private repository that answered 404. The vendored `TraceClient` is trace-client-java 0.6.1, whose server-wide switch file comment carries the same link.
- `Dockerfile` now copies `target/k8s-ai-operator-*.jar`, the artifact `./mvnw clean package` actually produces; the previous `edgescaleai-tech-interview-*.jar` pattern matched nothing, so `docker build` failed
- README *Security Notes* no longer claims LLM responses are schema-validated; it describes what is enforced (single JSON object + verb allowlist)
- `docs/user-guide.md` *Troubleshooting* no longer says a missing Bedrock model access shows up as a `500`; the parser's exception is caught by the controller and returned as a `400` with `allowed: false` and the Bedrock error as `reason`. The `BlockedCommands` metric descriptions (README, `docs/user-guide.md`) now say it also counts requests that failed to parse or execute
- `COMMANDS.md` now says the blocked `400` is also returned for a verb in `k8s.disallowed-verbs` that is also allowed, and that a failed DynamoDB/PostgreSQL write or CloudWatch publish is logged and still returns `200` (the bundled audit and metrics classes swallow their own write failures), so the `500` only covers an exception that escapes that step. `docs/user-guide.md` lists the `block_reason` audit field
- `COMMANDS.md` *Supported Resources* now says the resource list only steers the LLM: the operator does not check `resource`, and a `get` on another resource returns `200` with `No resources found for type: <resource>`. Its `apply` example prompt now shows the resource (`apply deployments -n staging`). `CONFIG.md` notes that a verb added to `k8s.allowed-verbs` other than `get` or `apply` returns `200` with `Unrecognised verb: <verb>`
- A model reply that is not valid JSON no longer puts the reply's text into the failure `reason`, the application log or the audit `block_reason`. A refusal can repeat the prompt, and Jackson's error message quoted the whole reply. The `reason` is now `Failed to parse Bedrock response into a command: invalid JSON` (or `Anthropic`), and the exception no longer keeps the Jackson error as its cause
- `POST /k8s/execute` now rejects a missing or blank `request_id` or `user_prompt` with a `400` (`allowed: false`, `reason: "<field> is required"`) before the LLM is called. A request with no `request_id` could previously run a command and return `200` with no audit record. A rejected request is logged, but neither audited nor counted in `BlockedCommands`
- A model reply with more than one text block, or with anything after the JSON object, is now rejected with a `400`. The parsers previously read the first command and dropped the rest, so the second command never reached the verb check or the audit record. This also rejects prose after the object, which was previously ignored
- `ExecuteRequest.toString()` no longer includes the user prompt, so anything that prints a request, such as Spring's `DEBUG` logging, leaves the prompt out
- `docs/ec2-setup.md` now uses `us-east-2`, the region `setup-ec2.sh` actually provisions in, instead of `us-east-1`. Its `nohup` command now passes `--aws.region`, without which the application would use the `us-east-1` default
- `docs/deploy.md`, `docs/ec2-setup.md`, `docs/eks-setup.md`, `docs/user-guide.md` and `USER_GUIDE.md` now say that the setup and deploy scripts are deprecated in favour of Terraform and Helm
- `docs/deploy.md` *Step 6* now rewrites the image placeholder, which the previous command left in place, and notes the `deploy.sh` image path bug (#45). It also lists the Java 21 prerequisite
- `docs/eks-setup.md` now says `setup-eks.sh` installs a missing `eksctl` or `kubectl` instead of exiting, and no longer implies the operator talks to the sample cluster. `docs/user-guide.md` now says the Kubernetes client is simulated, lists what `deploy.sh` needs on the EC2 instance, and deletes the key pair during teardown
- `CONFIG.md` now documents the `install` tag, the unusable-endpoint behaviour and the three local-profile keys it was missing, and says a missing version is sent as `unknown`. `COMMANDS.md` now covers the `400` and `415` that Spring returns for an unreadable body or a non-JSON `Content-Type`

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
