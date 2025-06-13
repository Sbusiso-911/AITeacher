// Updated with OpenAI API key - [current timestamp: 19;46]

const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { OpenAI } = require("openai");

// Initialize Firebase Admin
admin.initializeApp();
const db = admin.firestore();

exports.chat = functions.https.onCall(async (data, context) => {
  // --- BEGIN: Load API Key and Initialize OpenAI Client INSIDE the function ---
  let openaiApiKey;
  try {
    // Try to get it from Firebase config FIRST (this is for deployed functions)
    if (functions.config().openai && functions.config().openai.key) {
      openaiApiKey = functions.config().openai.key;
      console.log("OpenAI key loaded from Firebase config for 'chat' function.");
    } else {
      // Fallback for EMULATOR
      if (process.env.FUNCTIONS_EMULATOR === "true") {
        console.log("Attempting to load OPENAI_KEY from process.env for emulator.");
        openaiApiKey = process.env.OPENAI_KEY;
      }
      if (openaiApiKey) {
        console.log("OpenAI key loaded for emulator.");
      }
    }
  } catch (e) {
    console.error("Error loading OpenAI API key:", e.message);
  }

  if (!openaiApiKey) {
    console.error("CRITICAL: OpenAI API key is not available for the 'chat' function.");
    throw new functions.https.HttpsError(
        "internal",
        "AI service is not configured correctly (missing API key).",
    );
  }

  // Initialize OpenAI client HERE, inside the function
  const openai = new OpenAI({
    apiKey: openaiApiKey,
    timeout: 15000, // 15 second timeout
  });
  // --- END: Load API Key and Initialize OpenAI Client INSIDE the function ---

  // Verify user is authenticated
  if (!context.auth) {
    throw new functions.https.HttpsError(
        "unauthenticated",
        "Please sign in to chat",
    );
  }

  const { message } = data;
  const userId = context.auth.uid;

  if (!message || typeof message !== "string" || message.trim() === "") {
    throw new functions.https.HttpsError(
        "invalid-argument",
        "A non-empty message string is required.",
    );
  }

  try {
    // Get or create chat document for this user
    const chatRef = db.collection("chats").doc(userId);
    const doc = await chatRef.get();

    // Maintain conversation history (last 6 messages, so 3 pairs of user/assistant)
    let conversationMessages = doc.exists && doc.data().messages ? doc.data().messages : [];
    conversationMessages.push({ role: "user", content: message });
    conversationMessages = conversationMessages.slice(-6);

    // Call OpenAI API
    const completion = await openai.chat.completions.create({
      model: "gpt-3.5-turbo",
      messages: [
        {
          role: "system",
          content: "You are a helpful AI assistant. Respond conversationally.",
        },
        ...conversationMessages,
      ],
      temperature: 0.7,
      max_tokens: 500,
    });

    const reply = completion.choices[0].message.content;

    // Update conversation history
    conversationMessages.push({ role: "assistant", content: reply });
    conversationMessages = conversationMessages.slice(-6);
    await chatRef.set({ messages: conversationMessages }, { merge: true });

    return { reply };
  } catch (error) {
    console.error("OpenAI or Firestore Error in chat function:", error);
    if (error.response) {
      console.error("OpenAI API Error Status:", error.response.status);
      console.error("OpenAI API Error Data:", error.response.data);
    }
    throw new functions.https.HttpsError(
        "internal",
        "Failed to get AI response. " + (error.message || "Please try again later."),
        { errorDetails: error.message },
    );
  }
});

exports.getChatHistory = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
        "unauthenticated",
        "Please sign in to view chat history",
    );
  }

  const userId = context.auth.uid;

  try {
    const chatRef = db.collection("chats").doc(userId);
    const doc = await chatRef.get();

    if (doc.exists) {
      return { messages: doc.data().messages || [] };
    } else {
      return { messages: [] };
    }
  } catch (error) {
    console.error("Error getting chat history:", error);
    throw new functions.https.HttpsError(
        "internal",
        "Failed to get chat history",
    );
  }
});

exports.clearChat = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
        "unauthenticated",
        "Please sign in to clear chat",
    );
  }

  const userId = context.auth.uid;

  try {
    const chatRef = db.collection("chats").doc(userId);
    await chatRef.delete();
    return { success: true };
  } catch (error) {
    console.error("Error clearing chat:", error);
    throw new functions.https.HttpsError(
        "internal",
        "Failed to clear chat history",
    );
  }
});
