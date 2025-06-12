# AI Teacher

This project contains the AI Teacher Android application and a simple Node.js web server used to sync conversations across devices.

## Gemini API Example

The `scripts/gemini_openai_example.py` script demonstrates how to call the Gemini API using the OpenAI Python library. Update your API key and run the script to see a sample response.

## Web App Conversation Sync

A lightweight web interface under `webapp/` allows you to continue a chat session on a desktop browser. Run the server and open the page to view and send messages tied to an existing conversation ID from the Android app.

### Running the Web Server

```bash
cd webapp
npm install
npm start
```

The server listens on port `3000` by default. Navigate to `http://localhost:3000` in your browser.

### Getting the Conversation ID

On the phone, open the menu in a chat and choose **Copy Conversation ID**. Paste this ID into the web app to load the history and continue the conversation from your computer.

