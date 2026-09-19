# Architecture — NextGenTechForge platform

## Request path and hosting
The fictional NextGenTechForge commerce platform runs in AWS us-east-1. API Gateway accepts HTTPS requests and routes through a private load balancer to ECS Fargate services in private subnets across two availability zones. The payment-service runs in the production ECS cluster forge-production, ECS service payment-service, with at least two tasks. No containers accept public inbound traffic directly.

## Service communication
order-service owns order lifecycle and publishes OrderCreated events to Apache Kafka on Amazon MSK, topic orders.created.v1. payment-service consumes these events, authorizes the payment using an idempotency key derived from orderId, and publishes PaymentAuthorized or PaymentFailed to payments.events.v1. order-service consumes payment events to confirm or reject the order. Their business workflow is asynchronous through Kafka; there is no synchronous payment-to-order HTTP dependency. notification-service consumes order and payment events and sends fictional email notifications. Failed events retry with exponential backoff before entering a dead-letter topic for operator review.

## Data ownership
Each service owns a separate PostgreSQL database on Amazon RDS; services never query another service's tables. payment-service stores payment state and idempotency records. order-service stores orders and an outbox. A transactional outbox prevents a committed database change from losing its Kafka event. Redis on ElastiCache holds short-lived product cache entries and rate-limit counters, never authoritative payment state. Consumers deduplicate events because delivery is at least once.

## Observability and security
Containers send structured logs to CloudWatch Logs with correlationId and orderId; payment card data and secrets must never be logged. CloudWatch dashboards track HTTP error rate, p95 latency, ECS CPU and memory, Kafka consumer lag and failed payment count. IAM task roles grant least-privilege access; Secrets Manager injects database credentials. ALB target checks use /actuator/health/readiness and containers use /actuator/health/liveness. These describe the fictional platform, not infrastructure provisioned by this repository.
