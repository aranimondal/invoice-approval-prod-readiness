# Diagrams

There are four diagrams: two of the system as it's built today, and two of where it's going. Each one exists in three forms:

- **As an image** (SVG) on this page. It opens anywhere, including outside GitHub.
- **As a live Mermaid block** in the document that explains it. GitHub renders those inline.
- **As editable Mermaid source** (`.mmd`) in this folder. That's the version to change in a PR, so a diagram change gets reviewed like code.

| Diagram | Explained in | Source |
| --- | --- | --- |
| As-built: container view | [as-built.md](as-built.md#container-view), with a citation for every box | [as-built-container.mmd](as-built-container.mmd) |
| As-built: approval sequence | [as-built.md](as-built.md#sequence-approving-an-invoice) | [as-built-approval-sequence.mmd](as-built-approval-sequence.mmd) |
| Target: container view | [target-state.md](target-state.md#container-view). Dashed orange = doesn't exist yet | [target-container.mmd](target-container.mmd) |
| Target: approval and payment sequence | [target-state.md](target-state.md#approval-and-payment-sequence) | [target-approval-sequence.mmd](target-approval-sequence.mmd) |

## As-built: container view

What runs today: one JVM, two Maven modules. The domain module has no framework in it.

![As-built container view](img/as-built-container.svg)

## As-built: approval sequence

Note step 9: `findById` returns the live object. That's the root of the double-approve race explained in [as-built.md](as-built.md#what-i-corrected-in-the-agents-draft).

![As-built approval sequence](img/as-built-approval-sequence.svg)

## Target: container view

Persistence, the transactional outbox and PayEx. Everything with a dashed orange border is still to be built. Service Bus is provisioned, and deliberately not used in v1 ([ADR-001](../decisions/ADR-001-exactly-once-payex-submission.md)).

![Target container view](img/target-container.svg)

## Target: approval and payment sequence

PayEx is off the approver's request path. A timeout becomes `Unknown`, and we look the payment up before ever re-sending it.

![Target approval and payment sequence](img/target-approval-sequence.svg)

## Regenerating the SVGs

```
npx -y @mermaid-js/mermaid-cli -i docs/architecture/as-built-container.mmd -o docs/architecture/img/as-built-container.svg -b white
```

Do the same for each `.mmd` file. Rendering is also the quickest syntax check: GitHub only reports a bad Mermaid block as a render error on the page.
