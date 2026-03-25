# Contributing

## Thank You

Thank you for your interest in contributing to k8s-ai-operator! This guide will help you get started.

## Links

- [Discord](https://discord.gg/xXtuAQ2)

## Requirements

- A GitHub account
- Git installed on your local machine
- A Java IDE or text editor
- Java 21
- Maven 3.9+
- A basic understanding of Java and Spring Boot

### Optional (for fully local end-to-end testing, no AWS required)

- Docker (to run a local Postgres audit database)
- Minikube (to run a local Kubernetes cluster)
- An Anthropic API key (`ANTHROPIC_API_KEY`)

## Getting Started

1. [Sign up for GitHub](https://github.com/signup) if you don't have an account.
2. Fork the repository by clicking **Fork** at the top right of the repo page.
3. Clone your fork: `git clone https://github.com/<your-username>/k8s-ai-operator.git`
4. Open the project in your IDE.
5. Build the project: `./mvnw clean package`
   If you encounter errors, please open an issue.

### Local development workflow (no AWS required)

1. Start the local Postgres container: `docker compose up -d`
2. *(Optional)* Start Minikube: `./setup-minikube.sh` — not required since `K8sClientAdapter` returns mock responses
3. Run the application with the `local` profile:
   `ANTHROPIC_API_KEY=<your-key> ./mvnw spring-boot:run -Dspring-boot.run.profiles=local`

See the [Local Development](README.md#local-development-no-aws-required) section in the README for full details.

## Identifying What to Work On

### Issues

Work items are tracked as [GitHub issues](https://github.com/dmccoystephenson/k8s-ai-operator/issues).

### Milestones

Issues are grouped into [milestones](https://github.com/dmccoystephenson/k8s-ai-operator/milestones) representing upcoming releases.

## Making Changes

1. Make sure an issue exists for the work. If not, create one.
2. Switch to `develop`: `git checkout develop`
3. Create a branch: `git checkout -b <branch-name>`
4. Make your changes.
5. Test your changes (see [Testing](#testing)).
6. Commit: `git commit -m "Description of changes"`
7. Push: `git push origin <branch-name>`
8. Open a pull request against `develop`, link the related issue with `#<number>`.
9. Address review feedback.

## Testing

Run the unit tests with:

Linux: `./mvnw clean test`
Windows: `mvnw.cmd clean test`

If you see `BUILD SUCCESS`, the tests have passed.

## Questions

Open a [GitHub issue](https://github.com/dmccoystephenson/k8s-ai-operator/issues) with your question.
