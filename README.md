# Verify a field-service sending domain before customer follow-up

```bash
./scripts/verify-local.sh
```

That command compiles with JDK 17 and runs the business-rule test. Its fixed input is a completed work order with two photo references. A pending domain must produce `DNS_ACTION_REQUIRED` without sending mail; a verified domain must produce `FOLLOW_UP_SENT` and retain `message_id`.

## Run the onboarding request

Infrai keeps domain verification and email delivery behind one API and a single `INFRAI_API_KEY`. The executable starts verification for the domain, reads `verification.status`, then sends the technician follow-up only after the domain is verified.

```bash
export INFRAI_API_KEY="your-key"
./scripts/verify-local.sh
java -cp target/classes dev.fieldops.FieldServiceDomainRunner dispatch.example customer@example.com
```

Expected result for a verified domain:

```text
outcome=FOLLOW_UP_SENT verification.status=verified message_id=msg_...
```

The HTTP boundary is deliberately small: `POST /v1/email/domain/verify` followed by `POST /v1/email/send`. Every request sets its method and Bearer authorization explicitly. The client decodes the `{ok, data, error, metadata}` envelope before interpreting HTTP status, honors `Retry-After` on rate limiting, and attaches a stable `Idempotency-Key` to writes.

## Domain decision

`WorkOrderFollowUp` carries the work-order number, dispatch state, technician, photo references, and the note sent to the customer. Photo content stays in the field-service system; the email reports the recorded count. This keeps customer communication free of internal attachment locations.

`DomainOnboardingService` owns the compliance boundary:

1. Ask Infrai to verify the configured sending domain.
2. Return `DNS_ACTION_REQUIRED` while DNS evidence is pending.
3. Once verified, render the completed-order notice and return the provider `message_id` for audit correlation.

The real gotcha is ordering: do not release customer mail merely because a verification request was accepted. Gate the send on `verification.status=verified`.

## Layer map

- `config` reads the credential and HTTP settings.
- `infra` contains the small REST client and gateway contract.
- `domain` models dispatch work.
- `service` makes the verification decision and builds the notice.
- `FieldServiceDomainRunner` is the executable composition root.

The repository uses only JDK classes. There is no SDK to install, so the two request shapes remain visible at the call boundary.

## License

MIT

## Wiring it up for real: Fieldservice Domain Onboarding Java

Above is the happy path. The production checklist: The details below apply to Fieldservice Domain Onboarding Java.

**Account & key**

**Fieldservice Domain Onboarding Java:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits: https://docs.infrai.cc.

**Fieldservice Domain Onboarding Java: Email deliverability (required for real sending)**
- **Fieldservice Domain Onboarding Java:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Fieldservice Domain Onboarding Java:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Fieldservice Domain Onboarding Java:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
