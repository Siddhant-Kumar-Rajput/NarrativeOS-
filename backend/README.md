# NarrativeOS API

The API keeps Gemini credentials off the Android app. Grounded analysis is disabled by default and has a configurable daily request cap.

## Run locally

From the repository root, create a virtual environment and install the backend requirements:

```powershell
py -3.12 -m venv backend\.venv
backend\.venv\Scripts\python.exe -m pip install -r backend\requirements.txt
```

Set the Gemini key directly in your local PowerShell session. Do not put it in the Android app or commit it to the repository:

```powershell
$env:GEMINI_API_KEY = "YOUR_KEY"
$env:FIREBASE_PROJECT_ID = "YOUR_PROJECT_ID"
$env:NARRATIVEOS_CLIENT_TOKEN = "YOUR_APP_TOKEN"
$env:ENABLE_GROUNDED_ANALYSIS = "true"
$env:MAX_DAILY_ANALYSES = "10"
backend\.venv\Scripts\python.exe -m uvicorn backend.app.main:app --host 0.0.0.0 --port 8000
```

`/health` checks availability. `POST /api/v1/analyze` accepts either `{"input_type":"URL","content":"https://www.instagram.com/..."}` or `{"input_type":"TEXT","content":"..."}`. URL input is restricted to HTTPS Instagram and X hosts. Instagram metadata is retrieved through the pinned `parth-dl` adapter. Its `title` field contains at most the first 100 caption characters, so URL reports are labeled `PARTIAL`; post media and full captions are not analyzed yet. Login-gated, deleted, and inaccessible posts return a failure report without inferred claims. Automated X retrieval is not implemented; use the text input mode.

Gemini Google Search grounding can incur usage charges. The server requires explicit opt-in and caps analysis requests per day; review current provider pricing before enabling it. The in-memory cap is for a single-process prototype, not a billing limit.

## Connect a physical Android phone

Keep the phone and development PC on the same Wi-Fi network while the API is running. Find the PC's IPv4 address with `ipconfig`, then open **Server** in the app and enter `http://<PC-IPv4>:8000`. The Android debug build allows HTTP for local development; use HTTPS for any deployed backend. Windows Firewall may need to allow Python on the private network.

## Firebase project setup

Use the same Google Cloud project for Firebase and Cloud Run. In Firebase Console:

1. Add Firebase to the Google Cloud project.
2. Enable **Authentication > Sign-in method > Email/Password**.
3. Create a **Cloud Firestore Standard** database in `asia-south1` and start in production mode.
4. Grant the Cloud Run runtime service account the **Cloud Datastore User** role so the Admin SDK can write report summaries.

Register the Android Firebase app with package name `com.narrativeos` (the current `applicationId`). Add the debug SHA-1 fingerprint `3CEDDB499F2CD0E24F43B35A89E74304EED03D41` in Firebase Project Settings, confirm Google is enabled under Authentication providers, then download the updated config. The attached `google-services.json` was generated for `com.narrativeos.com`, so it does not match this app and must not be used as-is. Copy the Web application OAuth client ID from the corrected config into **Server > Google Web OAuth client ID**.

In the Android app's **Server** settings, set the backend URL, app access token, Firebase Web API key, and Google Web OAuth client ID. The Firebase Web API key and OAuth client ID are public client configuration, not service-account secrets; restrict the API key to the Identity Toolkit API in Google Cloud. Firebase ID and refresh tokens stay in app memory and are cleared when the app process exits, so users sign in again after restarting the app.

Firestore stores reports at `users/{firebase_uid}/analyses/{analysis_id}`. Documents include claims, verdicts, citations, limitations, and timestamps, but never the original pasted text. Users can list and delete their own reports. The backend uses Firebase Admin with Cloud Run Application Default Credentials; do not download or commit a service-account JSON key.

## Deploy on Google Cloud Run

The repository includes a Dockerfile for the FastAPI service and a `.gcloudignore` that excludes Android and local build files. Use Google Cloud Shell from the Cloud Console or an authenticated local `gcloud` CLI. Select a project with billing enabled, then run:

```bash
gcloud config set project YOUR_PROJECT_ID
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com identitytoolkit.googleapis.com firestore.googleapis.com
gcloud run deploy narrativeos-api \
	--source . \
	--region asia-south1 \
	--allow-unauthenticated \
	--port 8080 \
	--cpu 1 \
	--memory 512Mi \
	--min 0 \
	--max 1 \
	--concurrency 1 \
	--set-env-vars ENABLE_GROUNDED_ANALYSIS=false,MAX_DAILY_ANALYSES=10,FIREBASE_PROJECT_ID=YOUR_PROJECT_ID \
	--set-secrets GEMINI_API_KEY=gemini-api-key:latest,NARRATIVEOS_CLIENT_TOKEN=narrativeos-client-token:latest
```

Create the `gemini-api-key` and `narrativeos-client-token` secrets in Secret Manager before deployment, and allow the Cloud Run service identity to access them. Never put either value in source control, command history, or the APK. The resulting public service URL is the deployed GCP/Cloud Run link; `/health` reports readiness, and `/docs` provides interactive API documentation. Enter the URL, app token, and Firebase Web API key in **Server** settings. Leave `ENABLE_GROUNDED_ANALYSIS=false` until the rotated Gemini key is set and current Google Search grounding costs are reviewed.

Cloud Run has a monthly free usage tier for low-volume services, but a Google Cloud billing account is required. Charges may still arise from usage above free allowances, Cloud Build, Artifact Registry storage, network egress, or Gemini/Search. `--min 0` allows scale-to-zero; the first request after idle may be slow. `--max 1` keeps the prototype's in-memory request cap meaningful within a running instance, but that cap resets when the instance restarts and is not a billing guarantee. Configure a GCP budget alert before enabling paid AI calls.

The submission stack is now: Android/Kotlin client, Firebase Authentication, per-user Cloud Firestore report history, FastAPI on Cloud Run, and Gemini with Google Search grounding. The Android client is installed on a device; the Cloud Run URL is the public deployed backend link.