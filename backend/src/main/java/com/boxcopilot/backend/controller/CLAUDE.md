# Backend Controller Instructions

Controllers define the HTTP contract. Keep them thin: validate input, authorize at the boundary where appropriate, delegate business decisions to services, and map the result to the established DTO/error format.

- Preserve existing `/api/v1` paths, HTTP methods, status codes, and response shapes unless the task explicitly changes the contract.
- Do not put persistence queries, multi-step business rules, or transaction orchestration in controllers.
- Use existing validation annotations and the global exception handler instead of ad hoc error responses.
- Keep public endpoints deliberately separate from authenticated endpoints; public box/image access uses UUIDs or tokenized image URLs.
- For endpoint changes, update the matching controller test and the frontend service/types that consume it.

Run the focused controller test class first, then `mvn test` from `backend/`.
