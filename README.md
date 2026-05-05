# E-Commerce Spring Boot — Learning Journey

## The Idea

This project was built as a hands-on learning path to go from zero Spring Boot knowledge to being able to lead a backend team. Rather than following tutorials, the approach was to build a real e-commerce system, deploy it to AWS, break it under load, and fix it the right way — using microservices.

The goal is to experience and understand **why** architectural decisions are made, not just how to implement them.

---

## Architecture

The project follows a combination of:
- **Domain-Driven Design (DDD)** — business logic lives in the domain layer
- **Clean Architecture** — dependencies point inward, domain has no framework dependencies
- **Hexagonal Architecture** — ports and adapters pattern (domain defines interfaces, infrastructure implements them)
- **Feature-Based Design (FBD)** — code organized by feature module, not by technical layer

### Module Structure
```
src/main/java/org/example/springboot0/
├── product/
│   ├── api/              # REST controllers (adapters)
│   ├── application/      # Use cases, services, DTOs, mappers
│   ├── domain/           # Entities, repository interfaces (ports)
│   └── infrastructure/   # JPA repositories (adapters)
├── order/
├── customer/
├── category/
└── shared/               # Cross-cutting concerns (exceptions, responses, config)
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.0.5 |
| ORM | Spring Data JPA / Hibernate |
| Database (prod) | AWS RDS PostgreSQL |
| Database (dev) | H2 in-memory |
| Migrations | Flyway |
| Containerization | Docker |
| Registry | AWS ECR |
| Deployment | AWS Elastic Beanstalk |
| CI/CD | GitHub Actions |
| Load Testing | k6 |

---

## What Has Been Built

### Domain Modules
- **Category** — product categorization with Many-to-Many relationship
- **Product** — full CRUD, soft delete, pagination, full-text search
- **Customer** — customer management
- **Order** — order creation with order items, bidirectional JPA relationships

### Database Design (Flyway Migrations)
| Version | Description |
|---|---|
| V1 | Create categories table |
| V2 | Create customers table |
| V3 | Create products table |
| V4 | Create orders and order_items tables |
| V5 | Add FK constraint orders → customers |
| V6 | Create product_categories join table (Many-to-Many) |
| V7 | Add soft delete (deleted_at) to products |
| V8 | Add PostgreSQL full-text search vector (tsvector + GIN index) |
| V9 | Seed 10,000 products for load testing |
| V10 | Assign categories to seeded products |

### Key Features
- **Soft delete** — products are never hard-deleted, filtered via `@SQLRestriction`
- **Pagination** — all list endpoints support page/size
- **Full-text search** — `GET /api/products/search` with filters: query, price range, category, stock availability
- **Spring Profiles** — `dev` (H2) and `prod` (PostgreSQL) environments
- **Global exception handling** — consistent API error responses

### Infrastructure & DevOps
- **Docker multi-stage build** — optimized image size
- **AWS ECR** — private Docker image registry
- **AWS Elastic Beanstalk** — managed Docker environment
- **GitHub Actions CI/CD** — on push to `dev` or `master`, builds Docker image, pushes to ECR, deploys to EB
- **Custom IAM role** (`eb-ec2-ecr-pull-role`) — EC2 instance profile with ECR read permissions

### Git Branching Strategy
- `master` — stable, production deployments
- `dev` — integration branch, also triggers deployment
- `feature/*` — individual features, merged into dev

---

## The Performance Problem We Are Proving

### Context
The current system is a **modular monolith** — all modules run in the same Spring Boot process and share the same database connection pool.

### The Experiment
We seeded 10,000 products and built a heavy full-text search feature using PostgreSQL `tsvector`. The goal is to use k6 to simulate realistic traffic and prove the following:

> When the `product search` endpoint is under heavy load (200-1000 concurrent users), the `order` endpoints become slow too — even though orders have nothing to do with search.

This happens because:
- They share the same DB connection pool
- They share the same JVM heap and thread pool
- One module's load degrades the entire application

### How We Test It
Two k6 scripts run simultaneously:
- `k6/search-stress.js` — ramps up to 1000 concurrent users hitting search
- `k6/orders-measure.js` — measures orders response time during the stress test

Results are streamed to **Grafana** for real-time visualization.

---

## What's Next — Microservices Migration

Once the performance degradation is proven and measured, the plan is to extract independent services:

### Phase 3 — Extract Microservices
- **product-service** — owns product catalog and search, its own PostgreSQL database
- **order-service** — owns orders and order items, its own PostgreSQL database
- **customer-service** — owns customer data
- **api-gateway** — single entry point using Spring Cloud Gateway, routes requests to the right service

### Phase 4 — Compare Performance
Run the exact same k6 stress test against the microservices architecture. Product search load will no longer affect orders because they run in isolated processes with separate DB pools.

### Other Planned Improvements
- Stock quantity management with atomic transactions (prevent overselling)
- HTTPS on Elastic Beanstalk via AWS Certificate Manager
- AWS Secrets Manager for credentials
- CloudWatch monitoring and alerting
- Product reviews module

---

## Running Locally

```bash
# Dev profile (H2 in-memory database)
./mvnw spring-boot:run

# Build Docker image
docker build -t spring-boot-0 .

# Run with Docker
docker run -p 8080:8080 spring-boot-0
```

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/products` | List products (paginated) |
| GET | `/api/products/search` | Full-text search with filters |
| GET | `/api/products/{id}` | Get product by ID |
| POST | `/api/products` | Create product |
| PUT | `/api/products/{id}` | Update product |
| DELETE | `/api/products/{id}` | Soft delete product |
| GET | `/api/orders` | List orders |
| POST | `/api/orders` | Create order |
| GET | `/api/customers` | List customers |
| POST | `/api/customers` | Create customer |
| GET | `/api/categories` | List categories |
| POST | `/api/categories` | Create category |
