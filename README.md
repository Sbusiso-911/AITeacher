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

If you are running the Android app in an emulator it already points to
`http://10.0.2.2:3000`, which maps to the host machine. When testing on a
physical device you will need to edit `RemoteConversationService` and replace
the base URL with your computer's IP address.

### Getting the Conversation ID

On the phone, open the menu in a chat and choose **Copy Conversation ID**. Paste this ID into the web app to load the history and continue the conversation from your computer.

Messages typed on the web page will appear on the phone the next time the
conversation is opened. Likewise, sending a message from the phone immediately
writes it to the JSON store so that refreshing the browser shows the latest
history.

