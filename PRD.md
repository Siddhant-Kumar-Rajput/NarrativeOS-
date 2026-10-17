# NarrativeOS — Product Requirements Document (PRD)

**Version:** 1.0  
**Status:** Implementation specification  
**Product type:** Android application with a floating overlay and AI-powered content verification  
**Primary AI provider:** Google Gemini  
**Initial content-retrieval adapter:** `parth-dl` for eligible public Instagram content  
**Primary user interaction:** Floating bubble → submit content → analyze → evidence-backed report

---

# 1. Product Definition

## 1.1 What is NarrativeOS?

NarrativeOS is an AI-powered content analysis and fact-checking application that helps users evaluate factual claims made in social-media posts, Reels, videos, screenshots, and other online content.

The application allows users to submit content they encounter while browsing other apps. NarrativeOS extracts factual claims, searches for reliable supporting or contradicting evidence, compares the claims against that evidence, and produces a structured report explaining what is supported, contradicted, partially supported, or still unverified.

The product must distinguish between what a piece of content *claims* and what the available evidence *establishes*.

NarrativeOS is not intended to tell users what political, social, or personal opinions to hold. It should provide transparent evidence and explain uncertainty so that users can make better-informed judgments.

## 1.2 Core product principle

**Don't tell people what to believe. Show them what the evidence supports, what it contradicts, and what remains unknown.**

Every product, design, engineering, and AI decision must preserve this principle.

## 1.3 The problem being solved

Users frequently encounter claims in social-media content that are difficult to evaluate because:

- Claims are mixed with commentary, entertainment, emotional language, and personal opinions.
- Videos may contain spoken claims that are not written in the caption.
- Captions, screenshots, and comments may omit important context.
- Users may not know which sources are trustworthy.
- Search engines return information without necessarily comparing it against the specific claim.
- A post may contain several claims with different levels of evidential support.
- Some posts are deleted, inaccessible, private, login-gated, or unavailable through automated retrieval.
- Existing fact-checking workflows often require users to manually copy claims, search for sources, and compare information themselves.

NarrativeOS should reduce the effort required to move from encountering a claim to understanding the evidence relevant to it.

## 1.4 Primary value proposition

A user should be able to submit a social-media post with minimal effort and receive a transparent, claim-by-claim evidence report without manually researching every statement.

The product should make the difference between these three things explicit:

1. What the original content actually says or depicts.
2. What credible external sources establish.
3. What can and cannot reasonably be concluded from the available evidence.

---

# 2. Product Goals and Non-Goals

## 2.1 Goals

The product must:

1. Provide a fast entry point through an Android floating bubble.
2. Support pasting a content URL as the primary analysis workflow.
3. Support user-initiated capture of multiple screenshots while the user browses another application.
4. Support uploading existing screenshots.
5. Support uploading screen recordings and other supported video files.
6. Retrieve accessible public Instagram content using `parth-dl` as the initial retrieval adapter.
7. Extract factual claims from text, images, and supported video/audio content using Gemini.
8. Search external sources for evidence relevant to each claim.
9. Present claim-level findings, citations, evidence summaries, confidence, and limitations.
10. Provide useful fallback paths when automated retrieval fails.
11. Keep third-party API keys and secrets on the backend.
12. Make uncertainty and retrieval failures explicit rather than inventing missing content.

## 2.2 Non-goals for the initial MVP

The initial release does not need to:

- Monitor the user's screen continuously.
- Automatically capture content without an explicit user action.
- Read every post the user views.
- Bypass platform authentication, private-account restrictions, or protected-content restrictions.
- Log into Instagram or X on behalf of the user.
- Require users to create a NarrativeOS account.
- Maintain social-media accounts or publish content on behalf of users.
- Fact-check every comment, reply, or linked page automatically.
- Provide a comprehensive browser extension.
- Support every social-media platform through automated retrieval.
- Guarantee that a claim is objectively true or false.
- Replace professional journalism, legal analysis, medical advice, or primary-source research.
- Train a proprietary AI model.
- Introduce paid APIs without explicit budget limits and configuration.

## 2.3 Product success criteria

The MVP should be considered functionally successful when a user can:

- Submit a URL and receive a report or an accurate explanation of why the content could not be retrieved.
- Upload screenshots and analyze their visible content.
- Upload a supported video and analyze the media successfully processed by the system.
- Capture several screenshots in a single user-authorized session while scrolling through another app.
- Receive findings tied to evidence and traceable source citations.
- Understand when the system lacks enough evidence to reach a conclusion.

---

# 3. Target Users and Main Use Cases

## 3.1 Primary user

A person browsing social-media platforms who encounters a factual claim and wants to check it quickly.

The user may not be technically sophisticated and should not need to understand AI models, APIs, extraction libraries, or search-provider configurations.

## 3.2 Core use cases

### Use case A: Check an Instagram Reel

The user sees a Reel making a factual claim, opens the NarrativeOS bubble, pastes the Reel URL, and selects Analyze.

NarrativeOS attempts to retrieve the accessible caption, metadata, and media. If the media is available, Gemini extracts relevant factual claims. The system searches for external evidence and generates a report.

If the Reel cannot be retrieved, NarrativeOS tells the user and offers screenshot, text, and video-upload alternatives.

### Use case B: Check an X post or thread

The user submits an X post URL or pastes the post text.

NarrativeOS attempts to retrieve the post using an appropriately authorized retrieval method. If automated access is unavailable, the user can provide the text or screenshots.

The same analysis pipeline is used regardless of the retrieval method.

### Use case C: Check several screens of a long post or thread

The user starts a floating capture session, captures the first screen, scrolls, and captures additional screens. After finishing, the user reviews the screenshots and submits them together.

NarrativeOS extracts text across the images and combines relevant information without assuming that every screenshot belongs to the same post.

### Use case D: Check a video that cannot be retrieved by URL

The user records or saves a supported video and uploads it to NarrativeOS.

Gemini analyzes the available video and audio, identifies factual claims, and returns relevant timestamps where possible. The system then verifies the claims using external evidence.

### Use case E: Check an image containing a claim

The user uploads a screenshot, photograph, infographic, or image containing text.

NarrativeOS extracts readable text, identifies factual assertions, searches for evidence, and produces a report.

---

# 4. Supported Input Methods

NarrativeOS must support four input methods. All inputs must converge into the same downstream claim-extraction and evidence-verification pipeline.

## 4.1 Input method 1: Paste URL

**Priority: P0 — Primary workflow**

### User flow

1. The user opens NarrativeOS using the floating bubble or app interface.
2. The app displays a URL input field.
3. The user pastes a supported public content URL.
4. The app validates the URL format and supported platform.
5. The user taps Analyze.
6. The app displays an analysis progress screen.
7. The backend attempts to retrieve the content.
8. Retrieved content is passed into the analysis pipeline.
9. The app displays the final report or an actionable failure message.

### URL validation requirements

The backend must:

- Normalize supported URL formats where safe.
- Identify the expected platform from the parsed hostname.
- Reject unsupported URL schemes.
- Reject malformed URLs and unexpected hosts.
- Prevent server-side request forgery by restricting outbound requests and validating redirects.
- Avoid fetching arbitrary user-supplied URLs with unrestricted backend privileges.
- Handle shortened links only through controlled, validated redirect resolution.
- Limit the length of submitted URLs.
- Never interpret an arbitrary URL as an approved social-media URL merely because its text contains a platform name.

### Initial platform support

**Instagram:** Use `parth-dl` as the initial open-source retrieval adapter for eligible public posts and Reels.

The application must not assume that every public-looking Instagram URL can be retrieved. The library may fail when content is login-gated, removed, restricted, or affected by changes to Instagram's endpoints.

**X:** Design a separate adapter interface for future official API integration. Do not require X API credentials for the first Instagram-focused implementation.

**Other platforms:** Reject unsupported platforms with a clear explanation, while offering manual input options.

### Retrieval status

Every retrieval attempt must resolve to one of these states:

- `FULL`: The required content was retrieved sufficiently for the requested analysis.
- `PARTIAL`: Some relevant content was retrieved, but one or more components are missing.
- `FAILED`: The system could not retrieve usable content.
- `UNSUPPORTED`: The platform or content type is not supported.

A `PARTIAL` result must identify the missing components where known, such as video media, caption, audio, or metadata.

### URL failure handling

If retrieval fails, display a message explaining that the content could not be accessed automatically.

Offer these actions:

- Upload screenshots.
- Upload a screen recording or video.
- Paste the caption or post text.
- Try another URL.

Do not silently proceed as if the content had been retrieved.

## 4.2 Input method 2: Floating multi-screenshot capture

**Priority: P1 — Important feature**

### Objective

Allow a user to capture multiple screens from another application during one explicitly initiated capture session, without reopening NarrativeOS after every screenshot.

### User flow

1. The user opens the NarrativeOS floating bubble.
2. The user selects Capture screenshots.
3. NarrativeOS explains that Android requires screen-capture permission.
4. The user grants permission through the system interface.
5. NarrativeOS starts a user-authorized screen-capture session using Android's supported capture mechanisms.
6. A small, movable floating Capture control appears over the other application.
7. The user navigates to the desired content.
8. The user taps Capture.
9. NarrativeOS saves a still image of the current screen.
10. The user scrolls the underlying application.
11. The user taps Capture again.
12. The user repeats the process for as many screenshots as permitted by configured limits.
13. The user selects Finish capture.
14. NarrativeOS stops the capture session and displays the captured images.
15. The user can inspect, remove, or reorder images where appropriate.
16. The user taps Analyze to submit the selected images.

### Floating control requirements

The Capture control must:

- Be visually distinct from the underlying app.
- Remain small enough to avoid unnecessarily obstructing content.
- Be movable to a different screen position.
- Provide clear visual feedback after a successful capture.
- Avoid capturing the overlay itself where technically feasible.
- Support a clear Finish or Stop action.
- Never initiate continuous screenshot collection without the user's explicit action.

### Technical requirements

Use Android's supported screen-capture APIs, such as MediaProjection, with the permission and foreground-service behavior required by the targeted Android versions.

The implementation must account for:

- User consent before capture.
- Capture-session lifecycle and foreground-service requirements.
- Permission revocation.
- Process termination and session invalidation.
- Screen rotation and resolution changes.
- Temporary storage and cleanup.
- Overlay permission being separate from screen-capture permission.
- Apps or content that intentionally block screenshots or screen recording.
- System UI, navigation bars, and overlays appearing in captured images.

The implementation must not assume that `SYSTEM_ALERT_WINDOW` permission alone grants screenshot access.

The capture control should trigger a still-image capture from the active authorized session. It should not save an entire screen recording simply to create individual screenshots unless required by the chosen implementation.

### Capture limits

The app must have configurable limits for:

- Maximum screenshots per session.
- Maximum combined upload size.
- Maximum image dimensions or processing resolution.
- Maximum session duration.

The initial values should be selected based on Android testing, backend limits, and Gemini request constraints rather than assumed to be universal platform limits.

### Capture failure handling

If capture is unavailable or blocked, explain the limitation and offer existing screenshot upload as an alternative.

If the capture session ends unexpectedly, preserve already saved screenshots where possible and allow the user to submit them.

If a screenshot is blank or unreadable, flag it for review rather than treating it as successfully extracted content.

### Privacy requirements

- Capture only after explicit user initiation and permission.
- Do not continuously analyze the user's screen.
- Do not transmit every screen frame to the backend.
- Upload only screenshots selected for analysis.
- Stop capture when the user finishes or cancels.
- Provide a clear indication that capture is active.

## 4.3 Input method 3: Upload screenshots

**Priority: P0**

### User flow

1. The user selects Upload screenshots.
2. Android's system picker opens.
3. The user selects one or more image files.
4. NarrativeOS previews the selected images.
5. The user can remove unwanted files.
6. The user selects Analyze.
7. The backend validates and processes the images.

### Supported content

The system should handle common image formats supported by the chosen Android picker, backend image-processing stack, and Gemini API.

Unsupported formats must produce a clear error.

### Image-processing requirements

The pipeline must:

- Extract visible text using Gemini vision and/or OCR.
- Preserve the original wording where readable.
- Identify factual claims separately from opinions and questions.
- Interpret relevant image context.
- Handle multiple screenshots as a collection.
- Detect likely duplicate screenshots where practical.
- Preserve screenshot order where it matters.
- Avoid assuming that separate images belong to the same source or narrative.
- Flag unreadable, cropped, or ambiguous content.

If the user uploads several screenshots of the same long caption, the system may combine their extracted text when continuity is supported by the visible evidence.

It must not invent missing text between screenshots.

## 4.4 Input method 4: Upload a screen recording or video

**Priority: P0 for basic upload; advanced processing may be phased**

### Objective

Allow the user to submit a saved video or screen recording when URL retrieval is unavailable or insufficient.

### User flow

1. The user selects Upload video.
2. The system opens an Android file picker.
3. The user chooses a supported video file.
4. NarrativeOS displays the selected file and its basic metadata where available.
5. The user confirms the upload.
6. The backend validates the file.
7. The backend uploads the video to Gemini or processes it using another supported method.
8. The system waits for processing to finish.
9. Gemini extracts available speech, visible text, relevant visual events, and factual claims.
10. The extracted claims enter the shared verification pipeline.
11. The user receives a report with timestamps where available.

### Media-analysis requirements

The system should identify:

- Spoken factual assertions.
- Text visible in frames.
- Relevant visual context.
- Relevant timestamps.
- Claims that require external verification.
- Content that could not be interpreted reliably.

The AI must distinguish a speaker's assertion from what the video itself demonstrates.

For example, a speaker claiming that an event occurred does not establish that the event actually occurred.

### Technical requirements

- Validate MIME type and actual file content.
- Enforce configurable file-size and duration limits.
- Support asynchronous processing for larger files.
- Display upload, processing, and analysis states.
- Handle Gemini processing delays, failures, and quota limits.
- Avoid loading arbitrarily large files into backend memory.
- Delete temporary files according to the retention policy.
- Handle audio-unavailable, video-unavailable, and unsupported-format conditions explicitly.

### Video-analysis limitations

Gemini may sample video frames and may miss brief visual details, rapidly changing text, or short events. Audio transcription and visual extraction may also be imperfect.

The report must not imply that every frame or every spoken word was successfully interpreted.

If a claim depends on an ambiguous moment in a video, the system should flag that limitation or ask for a clearer recording.

---

# 5. Shared Content-Ingestion Architecture

All four input methods must produce a common internal representation before claim verification begins.

## 5.1 Architectural principle

The analysis pipeline must not depend on the particular input method.

A URL, screenshot, group of screenshots, and uploaded video should all be normalized into a common content object containing the extracted text, available media, source information, and processing limitations.

## 5.2 Pipeline stages

### Stage 1: Receive input

The backend receives an input type and the associated content.

Possible types:

- `URL`
- `IMAGE_SET`
- `VIDEO_FILE`
- `TEXT`

Although direct text input is a fallback rather than one of the four primary visual input paths, it must be supported because it is essential when automated retrieval fails.

### Stage 2: Validate input

Validate:

- Input type.
- File format and size, where applicable.
- URL hostname and redirects.
- Request limits.
- Supported platform or media type.
- Permissions and access to the submitted files.

Reject invalid input before invoking expensive AI or search services.

### Stage 3: Retrieve or process content

For URLs, use the appropriate platform adapter.

For screenshots, extract visible text and image context.

For videos, extract available audio, text, and visual information.

For pasted text, preserve the submitted wording.

### Stage 4: Create normalized content

The normalized content object should contain:

- Original user submission.
- Source URL, if available.
- Source platform, if identifiable.
- Extracted text.
- Available media references.
- Media timestamps, if applicable.
- Retrieval status.
- Extraction limitations.
- Processing metadata.

Do not store unnecessary raw media indefinitely.

### Stage 5: Extract factual claims

Gemini identifies individual factual claims from the normalized content.

Each claim should preserve:

- Original wording.
- Normalized wording, if needed.
- Claim category.
- Context needed for interpretation.
- Source location, such as a screenshot index or video timestamp.
- Whether the claim is verifiable from external evidence.

The system must not change a claim's meaning while simplifying it.

### Stage 6: Retrieve external evidence

For each material, verifiable claim, search for relevant independent sources.

Prioritize primary and official sources when appropriate, including original documents, public datasets, official reports, legislation, research papers, and direct statements.

Secondary sources may be used when primary evidence is unavailable or when expert interpretation is relevant.

### Stage 7: Compare claim and evidence

Gemini evaluates the retrieved evidence against the precise claim.

It must consider:

- Whether the source actually addresses the claim.
- Whether the source supports, contradicts, or qualifies the assertion.
- Whether the source is current enough for the topic.
- Whether the source is independent of other cited sources.
- Whether the claim depends on missing context.
- Whether available evidence is insufficient.

### Stage 8: Generate the report

Generate a structured claim-level report with evidence, citations, findings, confidence, and limitations.

The report must distinguish facts extracted from the submitted content from facts established by external evidence.

---

# 6. AI Responsibilities and Evidence Standards

## 6.1 Role of Gemini

Gemini is responsible for supported multimodal understanding and structured reasoning tasks, including:

- Extracting factual claims.
- Understanding screenshots and visible text.
- Analyzing supported video and audio inputs.
- Producing structured claim objects.
- Comparing claims with retrieved evidence.
- Explaining uncertainty and missing context.
- Generating the final report from the evidence available to the system.

Gemini must not be treated as the sole source of truth for current factual claims.

## 6.2 Role of the search provider

The web-search provider retrieves candidate sources relevant to the claims.

The search provider does not itself establish whether a claim is true.

The backend should store enough source information to identify and cite the evidence used, including title, URL, publication date where available, and relevant extracted passages or metadata.

Search quotas must be configurable. If quotas are exhausted, the system must not make paid requests unexpectedly.

## 6.3 Claim categories

The system should distinguish:

- Verifiable factual assertions.
- Opinions and value judgments.
- Predictions.
- Questions.
- Satire or apparent jokes.
- Personal experiences.
- Instructions or recommendations.

Only appropriate claims should receive factual-verification verdicts.

Opinions may be explained or contextualized, but must not automatically be labelled false because they are subjective.

## 6.4 Verdict definitions

**Supported:** The available credible evidence substantially supports the claim as stated.

**Contradicted:** The available credible evidence substantially conflicts with the claim as stated.

**Partially supported:** The claim contains both supported and unsupported elements, omits important qualifications, or is accurate only under a narrower interpretation.

**Unverified:** The available evidence is insufficient to establish support or contradiction.

An unverified claim must not automatically be described as false.

## 6.5 Evidence confidence

The report must communicate confidence in the evidence assessment, not an unsupported probability that the claim is true.

Confidence should reflect:

- Source quality.
- Relevance of sources.
- Agreement or disagreement across independent sources.
- Directness of the evidence.
- Completeness of retrieval.
- Ambiguity in the original claim.
- Quality of text or media extraction.

A numerical score may be introduced only with a defined scoring rubric and clear user-facing explanation. Do not present an arbitrary model-generated number as a statistically calibrated probability.

## 6.6 Citation requirements

Every material finding must be supported by traceable sources.

For each citation, show where possible:

- Source title.
- Publisher or organization.
- URL.
- Publication date.
- Access date.
- Relevant passage or concise evidence summary.

Never invent a citation, URL, quotation, publication date, or source attribution.

If a source cannot be opened or its relevant content cannot be checked, indicate that limitation.

## 6.7 Conflicting evidence

If credible sources disagree, the system must explain the disagreement.

It should not select a conclusion merely because more search results support one side.

Where necessary, distinguish differences in definitions, time periods, populations, measurements, or interpretations.

## 6.8 No-evidence behavior

If no reliable evidence is found:

- Return Unverified.
- Explain what was searched or what evidence was available.
- State relevant limitations.
- Avoid presenting search failure as proof of falsity.

---

# 7. User Experience and Screen Requirements

## 7.1 Floating bubble

The floating bubble is the primary entry point while the user browses other apps.

It must:

- Be movable.
- Remain visually compact.
- Open the NarrativeOS action interface when tapped.
- Respect the user's overlay permission.
- Be dismissible or disableable.
- Avoid blocking essential content.
- Provide a clear way to return to the full app.

The overlay permission and screen-capture permission must be handled separately.

## 7.2 Main action interface

The main interface should offer:

1. Paste URL.
2. Capture screenshots.
3. Upload screenshots.
4. Upload video.
5. Paste text as a fallback.

The URL workflow should be visually primary, with the other input methods easy to discover.

The interface must not imply that every platform is supported for automatic retrieval.

## 7.3 Processing screen

During analysis, show a clear current state, such as:

- Validating input.
- Retrieving content.
- Extracting claims.
- Searching for evidence.
- Comparing evidence.
- Preparing report.

These statuses should reflect actual backend progress. Do not display fabricated progress or claim that a stage completed when it did not.

Provide a cancellation action where technically feasible.

## 7.4 Results screen

The report should include:

- A short, neutral overview.
- The number of claims identified.
- Claim-by-claim verdicts.
- Original claim wording.
- Concise reasoning.
- Supporting and contradicting evidence.
- Citations.
- Evidence-confidence explanation.
- Missing context and limitations.
- Source-content retrieval status.

Each claim should be expandable to reveal further evidence.

The user should be able to open cited sources and return to the report without losing their place.

## 7.5 Error and empty states

Provide actionable messages for:

- Invalid URL.
- Unsupported platform.
- Private or inaccessible content.
- Login wall.
- Deleted content.
- Partial retrieval.
- Unsupported file.
- Oversized file.
- Failed upload.
- Failed video processing.
- Gemini quota exhaustion.
- Search quota exhaustion.
- No claims identified.
- No reliable evidence found.
- Network interruption.

Error messages should distinguish input problems, access restrictions, processing failures, and evidence limitations.

---

# 8. Technical Architecture

## 8.1 Android application

**Recommended stack:**

- Kotlin.
- Jetpack Compose.
- Android system file/photo picker.
- Android-supported overlay APIs.
- MediaProjection for the user-authorized multi-screenshot workflow.
- A foreground service where required by the target Android version.
- A backend API client.

The app should not contain provider secrets.

## 8.2 Backend

**Recommended stack:**

- Python.
- FastAPI.
- A deployment environment with explicit resource and cost limits.

Cloud Run is one possible deployment target, but deployment requirements, billing requirements, and free-tier eligibility must be verified before committing to it.

The backend should expose separate logical services or modules for:

- Input validation.
- Platform retrieval.
- Image and video processing.
- Claim extraction.
- Search and evidence retrieval.
- Evidence comparison.
- Report generation.
- Temporary-file cleanup.

## 8.3 Instagram retrieval adapter

Use `parth-dl` as the initial retrieval adapter.

Do not expose its local HTTP server directly to the public internet.

Integrate the library through a restricted backend worker or adapter and apply:

- Strict URL validation.
- Redirect-destination validation.
- Network egress restrictions.
- Request timeouts.
- Response-size limits.
- Download-size limits.
- Concurrency limits.
- Temporary-file cleanup.
- Error normalization.
- Tests for malformed URLs and inaccessible content.

Pin a reviewed release or commit. Do not assume that the library will continue working when Instagram changes its endpoints.

Do not provide the library with Instagram passwords, session cookies, or unrelated credentials.

## 8.4 Gemini integration

Use Gemini through the backend.

The backend should select models based on currently available API capabilities, supported input modalities, quality, latency, and cost.

Do not hard-code an assumed model name without checking current availability.

Use structured output for claim extraction and report-generation stages where supported.

For video files, follow the current Gemini API's requirements for upload, processing status, file limits, supported formats, and retention.

## 8.5 Search integration

Use a configurable search-provider interface so providers can be replaced without rewriting the analysis pipeline.

The provider should support:

- Querying by claim.
- Returning source URLs and metadata.
- Fetching relevant source content when permitted.
- Recording search failures and quota exhaustion.
- Enforcing request and cost limits.

If multiple providers are configured, use a predictable fallback order. Never silently exceed the configured budget.

## 8.6 Data persistence

A database is not mandatory for the first functional prototype.

The MVP may process analyses without permanent user accounts or a long-term analysis history.

Temporary media and intermediate files must have a defined cleanup policy.

If report history is added later, define its retention, deletion, and privacy behavior before implementation.

---

# 9. Security and Privacy Requirements

NarrativeOS processes user-submitted media that may contain personal information.

The implementation must:

- Request only permissions necessary for the selected feature.
- Explain why screen-capture permission is needed.
- Capture only after explicit user action.
- Upload only content the user has selected for analysis.
- Use HTTPS for backend communication.
- Keep API keys and provider secrets on the backend.
- Validate uploaded files and URLs.
- Protect against server-side request forgery.
- Restrict outbound network access from retrieval workers.
- Apply rate limits to prevent abuse.
- Avoid logging full screenshots, videos, API keys, or sensitive extracted text unnecessarily.
- Delete temporary files according to the documented retention policy.
- Avoid retaining social-media credentials.
- Never attempt to bypass access controls or screenshot protections.

The absence of user accounts does not eliminate the need for abuse prevention, secure API endpoints, or privacy controls.

---

# 10. Performance, Reliability, and Cost Controls

## 10.1 Performance

The system should provide prompt feedback after submission and show meaningful processing stages.

Video analysis may take longer than text or image analysis. Do not impose the same latency expectation on every input type.

Long-running jobs should use asynchronous processing rather than holding an HTTP request open indefinitely.

## 10.2 Reliability

The backend must handle:

- External API timeouts.
- Rate limits.
- Temporary platform failures.
- Partial retrieval.
- Unsupported media.
- Model errors.
- Malformed AI output.
- Missing citations.
- Interrupted uploads.
- Temporary storage failures.

A failure in one retrieval adapter should not break other input methods.

## 10.3 Cost control

The MVP should prioritize services with usable free tiers and predictable spending.

Before enabling a provider, confirm current API limits, pricing, signup requirements, and whether payment details are required.

Implement configurable limits for:

- Maximum video duration.
- Maximum file size.
- Maximum screenshots per analysis.
- Maximum AI requests per analysis.
- Maximum search requests per claim.
- Maximum retries.
- Maximum concurrent jobs.
- Daily or monthly usage.

If a quota is exhausted, return a clear error or offer a manual fallback. Do not make unexpected paid calls.

---

# 11. Testing Requirements

## 11.1 URL retrieval tests

Test:

- Accessible public Instagram post.
- Accessible public Reel.
- Carousel or multi-image post.
- Content with a caption but unavailable media.
- Login-gated content.
- Deleted content.
- Malformed URL.
- URL with an unexpected hostname.
- Redirect to an unexpected destination.
- Rate-limited or failed retrieval.

The expected result for inaccessible content is a clear failure or partial-retrieval status, not fabricated analysis.

## 11.2 Screenshot tests

Test:

- Single screenshot.
- Multiple screenshots from a long caption.
- Multiple screenshots with overlapping content.
- Images containing small text.
- Cropped or blurred text.
- Duplicate images.
- Screenshots from unrelated posts.
- Protected or blank capture.
- Permission denied.
- Capture session interrupted.
- User cancels the session.

## 11.3 Video tests

Test:

- Video with clear speech.
- Video with on-screen text.
- Video with both speech and text.
- Video with no speech.
- Video with poor audio.
- Briefly visible text.
- Unsupported or corrupted file.
- Oversized file.
- Processing timeout.
- Gemini quota exhaustion.

## 11.4 Evidence-verification tests

Test:

- A claim supported by official sources.
- A claim contradicted by official sources.
- A claim that is partly true but missing context.
- A claim for which no reliable evidence is found.
- A claim with conflicting credible sources.
- A subjective opinion.
- A prediction.
- A claim extracted incorrectly from the original content.

Verify that the system does not invent sources, confuse opinions with facts, or treat unavailable evidence as proof of falsity.

---

# 12. Acceptance Criteria

The MVP is ready for initial evaluation when the following criteria are met.

### URL analysis

- A user can submit a supported URL.
- The backend validates the URL before retrieval.
- The app displays a real retrieval status.
- Retrieved claims can enter the shared analysis pipeline.
- Retrieval failure triggers usable alternatives.

### Screenshot upload

- A user can select multiple images.
- The user can preview and remove images.
- The backend processes the selected images together.
- The report reflects only text and context supported by the images or other evidence.

### Video upload

- A user can upload a supported video.
- The backend validates the file.
- The app displays processing status.
- Gemini analyzes the media that was successfully processed.
- Claims and timestamps are returned where supported.
- Processing failures are communicated clearly.

### Floating capture

- A user can explicitly start a capture session.
- The required Android permission is requested.
- A floating Capture control appears when the session is active.
- The user can capture multiple screens while scrolling.
- The user can finish the session and review captured images.
- The user can submit the selected images together.
- Permission denial, session termination, and capture failure are handled without crashing the app.

### Evidence report

- Claims are reported individually.
- Verdict definitions are applied consistently.
- Findings have traceable citations when evidence is available.
- Unverified findings are not labelled false merely because evidence is missing.
- Retrieval limitations and uncertainty are visible.
- The system never claims to have inspected unavailable content.

---

# 13. Implementation Priorities

The project should be implemented in phases to minimize risk and preserve a working MVP.

## Phase 1 — Core vertical slice

1. Create the Android app shell.
2. Implement URL submission.
3. Create the FastAPI backend.
4. Integrate the Instagram retrieval adapter with safety controls.
5. Integrate Gemini claim extraction.
6. Integrate the search provider.
7. Generate a structured evidence report.
8. Test the full flow end to end.

**Exit criterion:** One accessible public Instagram post can be processed into a cited report, and inaccessible content produces a clear failure state.

## Phase 2 — Manual input fallbacks

1. Add screenshot upload.
2. Support multiple images per analysis.
3. Add text-paste fallback.
4. Add image OCR and claim extraction.
5. Test incomplete and ambiguous screenshots.

**Exit criterion:** Users can obtain an analysis even when automated Instagram retrieval fails, provided they supply usable content.

## Phase 3 — Video input

1. Add video file selection and upload.
2. Implement file validation and asynchronous processing.
3. Integrate Gemini video understanding.
4. Extract claims and timestamps where possible.
5. Test processing limits and failure handling.

**Exit criterion:** Supported user-provided videos can be processed and verified through the shared pipeline.

## Phase 4 — Floating multi-screenshot capture

1. Implement overlay permission handling.
2. Implement user-authorized screen capture.
3. Add the movable floating Capture control.
4. Support repeated still-image captures.
5. Add Finish capture and preview.
6. Test session lifecycle and permission revocation.
7. Submit captured screenshots through the existing image pipeline.

**Exit criterion:** Users can capture several screens while browsing another app, review the images, and analyze them together without repeatedly reopening NarrativeOS.

## Phase 5 — Additional platforms

1. Add a platform adapter interface.
2. Evaluate official API access and pricing for X.
3. Implement X retrieval only after access and cost are confirmed.
4. Add other platforms according to user demand and technical feasibility.

**Exit criterion:** Each additional platform has a tested retrieval path, documented restrictions, and a manual fallback.

---

# 14. Instructions to the AI Developer

Treat this document as the product specification.

Before implementation:

1. Inspect the existing repository and identify what already exists.
2. Identify the target Android SDK and supported Android versions.
3. Verify the current Gemini API capabilities and model names.
4. Verify the current Instagram retrieval library version and its limitations.
5. Verify deployment and API-provider cost constraints.
6. Identify any requirements that cannot be completed within the initial MVP timeline.
7. Propose an implementation plan that preserves the specified user experience and security requirements.

During implementation:

- Do not silently omit requirements.
- Do not fabricate API behavior or assume that permissions are interchangeable.
- Do not expose provider keys in the Android application.
- Do not add login or a database unless justified by a documented requirement.
- Do not build a separate fact-checking pipeline for every input method.
- Do not claim that content was analyzed if retrieval or media processing failed.
- Do not make paid external API calls beyond configured limits.
- Use explicit error states rather than silent fallbacks.
- Keep platform retrieval separate from claim extraction and evidence verification.

If a requirement cannot be implemented as written, explain the exact technical constraint, propose the closest compliant alternative, and identify what changes in the user experience.

## Final product definition

NarrativeOS is an Android-first, user-initiated content-verification application. Its primary workflow accepts a URL. Its fallback workflows accept multiple screenshots captured through a floating capture session, uploaded screenshots, uploaded video, or pasted text. All inputs feed a common AI-driven claim-extraction and evidence-verification pipeline that produces transparent, cited, claim-level findings while explicitly communicating uncertainty and access limitations.