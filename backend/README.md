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
$env:ENABLE_GROUNDED_ANALYSIS = "true"
$env:MAX_DAILY_ANALYSES = "10"
backend\.venv\Scripts\python.exe -m uvicorn backend.app.main:app --host 0.0.0.0 --port 8000
```

`/health` checks availability. `POST /api/v1/analyze` accepts either `{"input_type":"URL","content":"https://www.instagram.com/..."}` or `{"input_type":"TEXT","content":"..."}`. URL input is restricted to HTTPS Instagram and X hosts. Instagram metadata is retrieved through the pinned `parth-dl` adapter. Its `title` field contains at most the first 100 caption characters, so URL reports are labeled `PARTIAL`; post media and full captions are not analyzed yet. Login-gated, deleted, and inaccessible posts return a failure report without inferred claims. Automated X retrieval is not implemented; use the text input mode.

Gemini Google Search grounding can incur usage charges. The server requires explicit opt-in and caps analysis requests per day; review current provider pricing before enabling it. The in-memory cap is for a single-process prototype, not a production deployment.

## Connect a physical Android phone

Keep the phone and development PC on the same Wi-Fi network while the API is running. Find the PC's IPv4 address with `ipconfig`, then open **Server** in the app and enter `http://<PC-IPv4>:8000`. The Android debug build allows HTTP for local development; use HTTPS for any deployed backend. Windows Firewall may need to allow Python on the private network.