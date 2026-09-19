# Deployment guide — NextGenTechForge platform

## Environments
Development is local Docker, staging is the forge-staging ECS cluster, and production is forge-production in AWS us-east-1. Staging mirrors production topology at lower capacity. Configuration is externalized per environment; secrets come from AWS Secrets Manager. The same immutable image digest is promoted between staging and production.

## CI/CD and Docker
A pull request triggers Java 21 compilation, Gradle clean build, unit tests, dependency scanning and container image scanning. After review, CI builds a Docker image, tags it with the Git commit and pushes it to Amazon ECR. CI assumes an AWS deployment role using OIDC rather than storing AWS keys. Deployment to staging runs smoke tests; production requires an operator approval. Record the previous ECS task definition and image digest before each promotion.

## ECS rolling deployment
payment-service uses ECS Fargate rolling deployment with minimumHealthyPercent 100 and maximumPercent 200, maintaining at least two healthy production tasks. Register a new task definition referencing the promoted image digest, then update the ECS service. New tasks must pass ALB /actuator/health/readiness checks before receiving traffic. A 60-second grace period allows JVM initialization. ECS deployment circuit breaker with rollback is enabled. Liveness checks detect a stuck process; readiness checks determine whether traffic can be served. Wait for ECS service stability and run a synthetic order/payment smoke test before declaring success.

## Rollback procedure
If new tasks fail readiness, ECS circuit breaker automatically rolls back to the last completed deployment. If business error rate rises after a deployment completes, pause further releases, record the failed image digest and select the previous known-good ECS task definition revision. Update the affected service to that revision. Wait for service stability and healthy ALB targets, run the synthetic order/payment test, and verify CloudWatch error rate and Kafka lag return to baseline. Never delete the database to roll back. Schema migrations must use backward-compatible expand/contract changes because an image rollback does not undo database changes. Investigate the incident before attempting a new release.

## Monitoring
For 15 minutes after promotion or rollback, inspect CloudWatch dashboards and logs. Demo alert thresholds are HTTP 5xx above 1 percent for 5 minutes, p95 latency above 500 ms for 5 minutes, or Kafka consumer lag above 1000 events for 5 minutes. notification-service degradation may delay email without blocking payment processing. Escalate persistent failures to the on-call engineer. All procedures and platform names are fictional teaching material; the Java tools only return simulated observations and cannot perform these steps.
