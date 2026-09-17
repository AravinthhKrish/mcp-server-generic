# MCP completion and evaluation plan

The following requirements remain active until implementation and verification
provide evidence for each item. A passing coverage report alone is insufficient.

- [x] Authenticate all tool and resource routes; reject blank server credentials.
- [x] Standard MCP transport, discovery schemas, calls, lifecycle and protocol errors.
- [x] Google multipart/related contract verified by an enforcing HTTP fixture.
- [x] Streaming resumable uploads, bounded memory, progress and recovery.
- [x] OAuth consent, refresh, durable credential storage and user isolation.
- [x] Consistent parameter validation and sanitized provider error responses.
- [x] Accurate simulated flags and stateful stub metadata.
- [x] Drive timeouts, transient error handling and duplicate prevention.
- [x] Recoverable reel orchestration with persistent job status.
- [x] Config-derived source lists and evidence-based provider health.
- [x] Drive text reading, Gmail threads, batch quotes and topic digests.
- [x] Persistent cache and token storage.
- [x] Evaluation framework covering auth, protocol, provider contracts, failure
  paths, expired credentials, rate limits, interrupted and large uploads.
- [x] Run full tests and coverage; document external integration prerequisites.
- [x] Prepare the implementation as one reviewable, verified change set.

Remote commit and branch-push verification are delivery operations recorded outside
this implementation checklist.

Existing unrelated Kubernetes and desktop metadata changes are outside this work.
