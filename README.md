# AITeacher

This repository contains the Android application and a minimal Node.js web back‑end used to sync chat history between devices.

## Projects

- **app/** – Android source code.
- **webapp/** – Node.js server for the web version.

## Running the Web App

1. Install Node.js (version 18 or later).
2. Navigate to `webapp` and install dependencies:

```bash
cd webapp
npm install
npm start
```

The server listens on `http://localhost:3000` by default.

You can configure the Android app’s `WEB_APP_BASE_URL` constants to point to this server for local testing.
