# Windows code signing — owner guide (Azure Artifact Signing)

Why: Smart App Control blocked the unsigned probe on the owner's PC
(2026-10-29). Per R18 it stays on. Smart App Control allows apps signed with a
certificate from a trusted provider, and Microsoft names Artifact Signing as
the preferred way to sign. CI is already wired: once the repository variables
below exist, every push to `main` signs the Windows probe and verifies the
signatures. Nothing is stored in GitHub except non-secret IDs; CI signs in to
Azure with GitHub's OIDC token, so there is no password or key to leak.

Checked against Microsoft Learn on 2026-10-29 (sources at the end).

## Before you start: eligibility and cost

- **Individual developers must be in the United States or Canada** for a
  Public Trust certificate (the kind Smart App Control accepts).
- The Azure **billing account type must be Individual**, and its **legal name
  and sold-to address must match your government ID** exactly. Fix them
  before starting identity validation; they appear on the certificate.
- Identity validation takes **1 to 20 business days**.
- Cost: Basic tier, about US$9.99/month at the time of checking (verify the
  price shown in the portal). New Azure accounts may get a free credit.
- Microsoft Q&A threads report that individual onboarding has been paused at
  times. If "Individual Developer" is unavailable, stop and tell the
  developer; alternatives are an OV certificate from a commercial CA (about
  $150–300/year, hardware token or cloud HSM) or testing on a PC or VM
  without Smart App Control.

## Steps (Azure portal, about 30 minutes plus the validation wait)

1. **Azure account.** portal.azure.com → create an account with a
   pay-as-you-go subscription. Check Cost Management → billing account
   properties: Account type = Individual; legal name and address match your
   ID.
2. **Register the provider.** Subscriptions → your subscription → Resource
   providers → `Microsoft.CodeSigning` → Register.
3. **Create the signing account.** Search "Artifact Signing Accounts" →
   Create:
   - Resource group: new, `lockdin-signing`
   - Account name: e.g. `lockdinsigning` (3–24 letters/numbers, globally
     unique, must not start with "one")
   - Region: **East US** (endpoint `https://eus.codesigning.azure.net`), or
     the US region nearest you. Note the endpoint for that region.
   - Pricing: Basic
4. **Give yourself the verifier role.** Signing account → Access control
   (IAM) → Add role assignment → **Artifact Signing Identity Verifier** →
   your user. (Without it, "New identity" is greyed out.)
5. **Identity validation.** Signing account → Identity validations →
   **Individual Developer** → New identity → **Public** → fill in → Create.
   Watch your email for the verification link (status "Action Required").
   Wait for "Completed".
6. **Certificate profile** (after validation completes). Certificate
   profiles → Create → **Public Trust** → name e.g. `lockdin-public` →
   choose your verified identity → Create.
7. **Let GitHub sign in without a password (OIDC).**
   1. Microsoft Entra ID → App registrations → New registration →
      name `lockdin-github-signing` → Register. Note the **Application
      (client) ID** and **Directory (tenant) ID**.
   2. That app → Certificates & secrets → **Federated credentials** → Add →
      scenario "GitHub Actions deploying Azure resources" → Organization
      `tftechrobotics`, Repository `Lockd-In`, Entity **Branch**, Branch
      `main` → Add. (No client secret is created.)
   3. Signing account → Access control (IAM) → Add role assignment →
      **Artifact Signing Certificate Profile Signer** → select the app
      `lockdin-github-signing` (search for it; only users show by default).
8. **GitHub repository variables** (Settings → Secrets and variables →
   Actions → **Variables** tab → New repository variable). These are IDs,
   not secrets:

   | Name | Value |
   |---|---|
   | `AZURE_CLIENT_ID` | Application (client) ID from 7.1 |
   | `AZURE_TENANT_ID` | Directory (tenant) ID from 7.1 |
   | `AZURE_SUBSCRIPTION_ID` | Subscriptions → your subscription ID |
   | `AZURE_ARTIFACT_SIGNING_ENDPOINT` | e.g. `https://eus.codesigning.azure.net` |
   | `AZURE_ARTIFACT_SIGNING_ACCOUNT` | account name from step 3 |
   | `AZURE_ARTIFACT_SIGNING_PROFILE` | profile name from step 6 |

   Add `AZURE_ARTIFACT_SIGNING_ACCOUNT` last: its presence switches signing on.
9. Tell the developer. The next CI run signs both architectures; each
   download then contains `SIGNING.txt` showing "Valid" for every file.

## What CI does

- On `main` only, when the variables exist: Azure sign-in via OIDC
  (`azure/login` v3.1.0, pinned by commit), sign every `.exe`/`.dll` with
  SHA-256 and Microsoft's timestamp server (`azure/artifact-signing-action`
  v2.0.0, pinned by commit), then verify each signature with
  `Get-AuthenticodeSignature` and fail the job if any is not Valid.
- Otherwise: builds stay unsigned, and `SIGNING.txt` plus a CI notice say so.
- Pull requests never sign.

## Known risks (to confirm on the PC, not assumed)

- Smart App Control may also weigh cloud reputation for brand-new software.
  Microsoft's Smart App Control signing page lists only the trusted-provider
  requirement, but Q&A reports exist of signed apps still blocked. If that
  happens, collect Event Viewer → Applications and Services Logs → Microsoft
  → Windows → CodeIntegrity → Operational, events 3077, which name each
  blocked file.
- The probes are .NET single-file builds that extract native runtime
  libraries to a temp folder at first run. Those libraries are signed by
  Microsoft, so they should pass; event 3077 would show otherwise, and the
  fix is to publish them beside the exe and sign them too.

## Sources
- Artifact Signing quickstart — learn.microsoft.com/azure/artifact-signing/quickstart
- Code signing for Smart App Control — learn.microsoft.com/windows/apps/develop/smart-app-control/code-signing-for-smart-app-control
- Artifact Signing GitHub Action and OIDC guide — github.com/Azure/artifact-signing-action
- Signed app blocked by Smart App Control (Q&A) — learn.microsoft.com/answers/questions/5791210
