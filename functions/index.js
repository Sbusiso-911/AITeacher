require('dotenv').config();

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { setGlobalOptions } = require("firebase-functions/v2");
const logger = require("firebase-functions/logger");
const functions = require("firebase-functions");

const admin = require("firebase-admin");
admin.initializeApp();
const db = admin.firestore();

const { OpenAI } = require("openai");
const Anthropic = require("@anthropic-ai/sdk");
const { GoogleGenerativeAI, HarmCategory, HarmBlockThreshold } = require("@google/generative-ai");


// Base tool definitions shared across providers
const BASE_TOOLS = [
  {
    type: "function",
    name: "get_weather",
    description: "Fetches the current weather for a given city.",
    parameters: {
      type: "object",
      properties: {
        location: { type: "string", description: "City name" },
        unit: {
          type: "string",
          description: "Temperature unit",
          enum: ["celsius", "fahrenheit"],
        },
      },
      required: ["location", "unit"],
      additionalProperties: false,
    },
    strict: true,
  },
  {
    type: "function",
    name: "set_calendar_reminder",
    description: "Sets a reminder on the user's calendar (mock).",
    parameters: {
      type: "object",
      properties: {
        title: { type: "string" },
        start_time_iso: { type: "string" },
        description: { type: ["string", "null"] },
      },
      required: ["title", "start_time_iso"],
      additionalProperties: false,
    },
    strict: true,
  },
  {
    type: "function",
    name: "send_email_by_voice",
    description: "Creates an email draft (mock).",
    parameters: {
      type: "object",
      properties: {
        recipient: { type: "string" },
        subject: { type: "string" },
        body: { type: "string" },
      },
      required: ["recipient", "subject", "body"],
      additionalProperties: false,
    },
    strict: true,
  },
  {
    type: "function",
    name: "make_phone_call",
    description: "Initiates a phone call (mock).",
    parameters: {
      type: "object",
      properties: {
        phone_number: { type: "string" },
        contact_name: { type: ["string", "null"] },
      },
      required: ["phone_number"],
      additionalProperties: false,
    },
    strict: true,
  },
  {
    type: "function",
    name: "set_alarm",
    description: "Sets an alarm on the device clock (mock).",
    parameters: {
      type: "object",
      properties: {
        hour: { type: "integer" },
        minute: { type: "integer" },
        message: { type: ["string", "null"] },
      },
      required: ["hour", "minute"],
      additionalProperties: false,
    },
    strict: true,
  },
  {
    type: "function",
    name: "start_meeting_recording",
    description: "Starts an audio recording for a meeting (mock).",
    parameters: {
      type: "object",
      properties: {
        topic: { type: ["string", "null"] },
      },
      required: [],
      additionalProperties: false,
    },
    strict: true,
  },
];

const toolHandlers = {
  async get_weather({ location, unit }) {
    const u = unit === "fahrenheit" ? "°F" : "°C";
    return `The weather in ${location} is 25${u} and sunny (demo).`;
  },
  async set_calendar_reminder({ title, start_time_iso }) {
    return `Reminder '${title}' set for ${start_time_iso} (demo).`;
  },
  async send_email_by_voice({ recipient, subject }) {
    return `Draft email to ${recipient} with subject '${subject}' created (demo).`;
  },
  async make_phone_call({ phone_number, contact_name }) {
    const target = contact_name || phone_number || "unknown number";
    return `Pretend calling ${target}.`;
  },
  async set_alarm({ hour, minute, message }) {
    const time = `${hour.toString().padStart(2, "0")}:${minute.toString().padStart(2, "0")}`;
    return `Alarm set for ${time}${message ? ` (${message})` : ""} (demo).`;
  },
  async start_meeting_recording({ topic }) {
    return `Started recording${topic ? ` for ${topic}` : ""} (demo).`;
  },
};

function getTools(useResponses) {
  return BASE_TOOLS.map(t => {
    if (useResponses) {
      return {
        type: 'function',
        name: t.name,
        description: t.description,
        parameters: t.parameters,
        strict: t.strict,
      };
    }
    return {
      type: 'function',
      function: {
        name: t.name,
        description: t.description,
        parameters: t.parameters,
      },
    };
  });
}

setGlobalOptions({ region: "us-central1", cpu: "gcf_gen1" });

const SYSTEM_PROMPT_TEXT = "You are a helpful AI assistant. Respond conversationally and concisely.";

const AVAILABLE_MODELS = [
  { id: 'gpt-4o-2024-08-06', name: 'GPT-4o', provider: 'openai', description: 'Flagship OpenAI model, multimodal.', supportsTools: true },
  { id: 'gpt-4o-mini-2024-07-18', name: 'GPT-4o mini', provider: 'openai', description: 'Smaller, faster, multimodal OpenAI model.' },
  { id: 'gpt-4.1', name: 'GPT-4.1', provider: 'openai', description: 'Advanced OpenAI model for text generation.', supportsTools: true, usesResponses: true },
  { id: 'gpt-4o-search-preview', name: 'GPT-4o Search Preview', provider: 'openai', description: 'OpenAI model with web search capabilities (web search not enabled in this app version).', limitedParams: true },
  { id: 'claude-opus-4-20250514', name: 'Claude Opus 4', provider: 'anthropic', description: 'Most capable Claude 4 model from Anthropic.' },
  { id: 'claude-sonnet-4-20250514', name: 'Claude Sonnet 4', provider: 'anthropic', description: 'Balanced Claude 4 model from Anthropic.' },
  { id: 'gemini-2.0-flash', name: 'Gemini 2.0 Flash', provider: 'google', description: 'Google\'s fast Gemini 2.0 model.' },
  { id: 'gemini-1.5-flash-latest', name: 'Gemini 1.5 Flash (Latest)', provider: 'google', description: 'Fast and versatile Google model (latest alias).' },
  { id: 'gemini-1.5-pro-latest', name: 'Gemini 1.5 Pro (Latest)', provider: 'google', description: 'Google\'s advanced model (latest alias).' },
];

const getDefaultModelId = () => {
  const openAIModels = AVAILABLE_MODELS.filter(m => m.provider === 'openai');
  if (openAIModels.length > 0) return openAIModels[0].id;
  const anthropicModels = AVAILABLE_MODELS.filter(m => m.provider === 'anthropic');
  if (anthropicModels.length > 0) return anthropicModels[0].id;
  const googleModels = AVAILABLE_MODELS.filter(m => m.provider === 'google');
  if (googleModels.length > 0) return googleModels[0].id;
  return AVAILABLE_MODELS.length > 0 ? AVAILABLE_MODELS[0].id : 'gpt-4o-2024-08-06';
};

function getApiKey(provider) {
  try {
    if (provider === 'openai') return process.env.OPENAI_API_KEY || functions.config().openai?.key;
    if (provider === 'anthropic') return process.env.ANTHROPIC_API_KEY || functions.config().anthropic?.key;
    if (provider === 'google') return process.env.GOOGLE_API_KEY || functions.config().google?.key;
  } catch (e) { logger.warn(`Error accessing config for ${provider}: ${e.message}`); }
  return null;
}

function buildChatPayload(modelConfig, messages, extra = {}) {
  const payload = { model: modelConfig.id, messages, ...extra };
  if (!modelConfig.limitedParams) {
    payload.temperature = 0.7;
    payload.max_tokens = 1500;
  }
  return payload;
}

exports.getAvailableModels = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Authentication required.");
  return { models: AVAILABLE_MODELS, defaultModel: getDefaultModelId() };
});

function adaptConversationHistoryForProvider(dbHistory, provider, currentMessageContent = null) {
  let history = JSON.parse(JSON.stringify(dbHistory));
  if (currentMessageContent) {
    history.push({ role: "user", content: currentMessageContent });
  }

  if (provider === 'google') {
    return history.map(msg => ({
      role: msg.role === 'assistant' ? 'model' : 'user',
      parts: [{ text: msg.content || "" }],
    })).filter(msg => msg.parts[0].text && msg.parts[0].text.trim() !== "");
  }
  return history.filter(msg => msg.content && msg.content.trim() !== "");
}

function normalizeError(error, provider, modelConfigPassed = null) {
  const modelIdForError = modelConfigPassed ? modelConfigPassed.id : 'unknown model';
  logger.error(`Error with ${provider} API (Model: ${modelIdForError}):`, error.response?.data || error.message || error);
  let message = `AI provider (${provider}) encountered an error. Please try another model or contact support if the issue persists.`;

  const status = error.status || error.response?.status;

  if (status === 401) message = `Invalid or missing API key for ${provider}. Please check server configuration.`;
  else if (status === 429) message = `Rate limit exceeded for ${provider}. Please try again later.`;
  else if (status === 400 && error.message && error.message.toLowerCase().includes("model") && (error.message.toLowerCase().includes("not found") || error.message.toLowerCase().includes("does not exist"))) {
    message = `The selected model '${modelIdForError}' for ${provider} was not found or is not accessible with the current API key.`;
  }
  else if (error.message) message = `Error from ${provider}: ${error.message.substring(0, 200)}`;
  else if (typeof error === 'string') message = error.substring(0, 200);

  if (provider === 'google') {
    const feedback = error.response?.candidates?.[0]?.safetyRatings || error.response?.promptFeedback;
    if (feedback?.blockReason) {
      message = `Content blocked by Google (${modelIdForError}): ${feedback.blockReason}.`;
    } else if (feedback && Array.isArray(feedback)) {
      const highSeverityRating = feedback.find(r => r.severity?.startsWith('HARM_SEVERITY_HIGH'));
      if (highSeverityRating) message = `Content may violate Google's safety policy (${highSeverityRating.category}).`;
    }
  }
  if (provider === 'anthropic') {
    if (error.type === 'error' && error.error?.type === 'overload_error') {
      message = "Anthropic API is currently overloaded. Please try again later.";
    } else if (error.type === 'error' && error.error?.type === 'invalid_request_error' && error.error?.message?.toLowerCase().includes("model is invalid")) {
      message = `The Anthropic model ID '${modelIdForError}' is invalid or not accessible.`;
    } else if (error.status === 404 && error.error?.type === 'error' && error.error?.error?.type === 'not_found_error') {
      message = `The Anthropic model ID '${modelIdForError}' was not found.`;
    }
  }
  return message;
}


exports.chat = onCall({
  timeoutSeconds: 90,
  memory: '1GiB'
}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Authentication required.");

  const { message: userMessageContent, model: requestedModelId } = request.data;
  const userId = request.auth.uid;

  if (!userMessageContent || typeof userMessageContent !== "string" || userMessageContent.trim() === "") {
    throw new HttpsError("invalid-argument", "A non-empty message is required.");
  }

  const modelIdToUse = requestedModelId || getDefaultModelId();
  const modelConfig = AVAILABLE_MODELS.find(m => m.id === modelIdToUse);

  if (!modelConfig) {
    throw new HttpsError("invalid-argument", `Invalid model selected: ${modelIdToUse}.`);
  }

  const apiKey = getApiKey(modelConfig.provider);
  if (!apiKey) {
    throw new HttpsError("failed-precondition", `API key for ${modelConfig.provider} is not configured on the server.`);
  }

  const chatRef = db.collection("chats").doc(userId);
  let dbConversationMessages = [];
  try {
    const doc = await chatRef.get();
    if (doc.exists && doc.data().messages) {
      dbConversationMessages = doc.data().messages;
    }
  } catch (dbError) {
    logger.error("Error fetching chat history:", dbError);
  }

  let replyText = "";
  logger.info(`User ${userId} chatting with ${modelConfig.provider} model: ${modelConfig.id}`);

  try {
    if (modelConfig.provider === 'openai') {
      const openai = new OpenAI({ apiKey });
      const messagesForAPI = adaptConversationHistoryForProvider(dbConversationMessages, 'openai', userMessageContent);
      const history = messagesForAPI.slice(-20);

      if (modelConfig.usesResponses) {
        const basePayload = { model: modelConfig.id, input: history };
        if (modelConfig.supportsTools) {
          basePayload.tools = getTools(true);
        }
        let firstResponse;
        let useChatFallback = false;
        try {
          firstResponse = await openai.responses.create(basePayload);
        } catch (err) {
          const msg = err.message || '';
          if (msg.includes('not supported with the Responses API')) {
            logger.warn(`Model ${modelConfig.id} not supported with Responses API. Falling back to Chat Completions.`);
            useChatFallback = true;
          } else if (modelConfig.supportsTools && msg.includes('tools is not supported')) {
            logger.warn(`Model ${modelConfig.id} reported tools unsupported, retrying without tools.`);
            delete basePayload.tools;
            firstResponse = await openai.responses.create(basePayload);
          } else {
            throw err;
          }
        }
        if (!useChatFallback) {
          let toolCalls = Array.isArray(firstResponse.output) ? firstResponse.output.filter(o => o.type === 'function_call') : [];
          replyText = firstResponse.output_text || '';
          if (toolCalls.length > 0) {
            const toolOutputs = [];
            for (const call of toolCalls) {
              const name = call.name;
              let args = {};
              try { args = JSON.parse(call.arguments || '{}'); } catch (e) {}
              const handler = toolHandlers[name];
              const result = handler ? await handler(args) : `Tool '${name}' not implemented.`;
              toolOutputs.push({ type: 'function_call_output', call_id: call.call_id, output: result });
            }
            const followUp = await openai.responses.create({
              model: modelConfig.id,
              input: [...history, ...toolCalls, ...toolOutputs],
              tools: modelConfig.supportsTools ? getTools(true) : undefined,
            });
            replyText = followUp.output_text || '';
          }
        } else {
        const chatPayload = buildChatPayload(modelConfig, history);
          if (modelConfig.supportsTools) {
            chatPayload.tools = getTools(false);
            chatPayload.tool_choice = 'auto';
          }
          let chatResp = await openai.chat.completions.create(chatPayload);
          replyText = chatResp.choices[0].message.content || '';
          let finishReason = chatResp.choices[0].finish_reason;
          let historyForTools = history;
          if (finishReason === 'tool_calls' && chatResp.choices[0].message.tool_calls) {
            historyForTools = [...historyForTools, chatResp.choices[0].message];
            const toolResults = [];
            for (const call of chatResp.choices[0].message.tool_calls) {
              const name = call.function.name;
              let args = {};
              try { args = JSON.parse(call.function.arguments || '{}'); } catch (e) {}
              const handler = toolHandlers[name];
              const result = handler ? await handler(args) : `Tool '${name}' not implemented.`;
              toolResults.push({ role: 'tool', tool_call_id: call.id, content: result });
            }
            const secondResponse = await openai.chat.completions.create(
              buildChatPayload(modelConfig, [...historyForTools, ...toolResults])
            );
            replyText = secondResponse.choices[0].message.content || '';
            finishReason = secondResponse.choices[0].finish_reason;
          }
          if (finishReason === 'length') replyText += ' ... (output truncated)';
        }
      } else {
        const basePayload = buildChatPayload(modelConfig, history);
        if (modelConfig.supportsTools) {
          basePayload.tools = getTools(false);
          basePayload.tool_choice = 'auto';
        }
        let firstResponse;
        try {
          firstResponse = await openai.chat.completions.create(basePayload);
        } catch (err) {
          if (modelConfig.supportsTools && err.message && err.message.includes('tools is not supported')) {
            logger.warn(`Model ${modelConfig.id} reported tools unsupported, retrying without tools.`);
            delete basePayload.tools;
            delete basePayload.tool_choice;
            firstResponse = await openai.chat.completions.create(basePayload);
          } else {
            throw err;
          }
        }
        replyText = firstResponse.choices[0].message.content || '';
        let finishReason = firstResponse.choices[0].finish_reason;
        let historyForTools = history;
        if (finishReason === 'tool_calls' && firstResponse.choices[0].message.tool_calls) {
          historyForTools = [...historyForTools, firstResponse.choices[0].message];
          const toolResults = [];
          for (const call of firstResponse.choices[0].message.tool_calls) {
            const name = call.function.name;
            let args = {};
            try { args = JSON.parse(call.function.arguments || '{}'); } catch (e) {}
            const handler = toolHandlers[name];
            const result = handler ? await handler(args) : `Tool '${name}' not implemented.`;
            toolResults.push({ role: 'tool', tool_call_id: call.id, content: result });
          }
          const secondResponse = await openai.chat.completions.create(
            buildChatPayload(modelConfig, [...historyForTools, ...toolResults])
          );
          replyText = secondResponse.choices[0].message.content || '';
          finishReason = secondResponse.choices[0].finish_reason;
        }
        if (finishReason === 'length') replyText += ' ... (output truncated)';
      }
    } else if (modelConfig.provider === 'anthropic') {
      const anthropic = new Anthropic({ apiKey });
      const messagesForAPI = adaptConversationHistoryForProvider(dbConversationMessages, 'anthropic', userMessageContent);
      const response = await anthropic.messages.create({
        model: modelConfig.id,
        system: SYSTEM_PROMPT_TEXT,
        messages: messagesForAPI.slice(-20),
        max_tokens: 1500,
        temperature: 0.7,
      });

      if (response.stop_reason === 'max_tokens') {
        replyText = response.content.map(block => block.text).join(" ").trim() + " ... (output truncated)";
      } else if (response.stop_reason === 'refusal') {
        logger.warn(`Anthropic model ${modelConfig.id} refused content. Stop reason: refusal. Content: ${JSON.stringify(response.content)}`);
        const partialText = response.content.map(block => block.text).join(" ").trim();
        replyText = partialText ? `${partialText} (The model declined to fully complete this request due to its content policy.)` : "I am unable to process this request due to content guidelines.";
      } else {
        replyText = response.content.map(block => block.text).join(" ").trim();
      }

    } else if (modelConfig.provider === 'google') {
      const genAI = new GoogleGenerativeAI(apiKey);
      const modelInstance = genAI.getGenerativeModel({
        model: modelConfig.id,
        safetySettings: [
          { category: HarmCategory.HARM_CATEGORY_HARASSMENT, threshold: HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE },
          { category: HarmCategory.HARM_CATEGORY_HATE_SPEECH, threshold: HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE },
          { category: HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT, threshold: HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE },
          { category: HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT, threshold: HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE },
        ],
      });

      const chatHistoryForGoogle = adaptConversationHistoryForProvider(dbConversationMessages, 'google');

      const chatSession = modelInstance.startChat({
        history: chatHistoryForGoogle.slice(-20),
        generationConfig: {
          temperature: 0.7,
          maxOutputTokens: 1500,
        }
      });

      const result = await chatSession.sendMessage(userMessageContent);

      if (result.response) {
        const candidate = result.response.candidates?.[0];
        if (candidate) {
          replyText = candidate.content?.parts?.map(part => part.text).join(" ")?.trim() || "";
          if (candidate.finishReason === 'MAX_TOKENS') {
            replyText += " ... (output truncated)";
          } else if (candidate.finishReason === 'SAFETY') {
            logger.warn(`Google GenAI response for ${modelConfig.id} finished due to SAFETY. Ratings: ${JSON.stringify(candidate.safetyRatings)}`);
            replyText = replyText ? `${replyText} (The model stopped generating due to its safety policy.)` : "I am unable to process this request due to content policy.";
          } else if (candidate.finishReason === 'OTHER') {
            logger.warn(`Google GenAI response for ${modelConfig.id} finished due to OTHER reasons.`);
            replyText = replyText || "The AI response was stopped for other reasons.";
          }
        } else if (result.response.promptFeedback?.blockReason) {
          logger.warn(`Google GenAI prompt blocked for ${modelConfig.id}. Reason: ${result.response.promptFeedback.blockReason}`);
          replyText = `Your request was blocked by the content filter: ${result.response.promptFeedback.blockReason}.`;
        }
      }
    }

    if (!replyText) {
      replyText = "The AI did not provide a response for this query. It might be due to content policies or an internal error.";
      logger.warn(`Empty reply from ${modelConfig.provider} model: ${modelConfig.id} (final check) for user ${userId}, message: "${userMessageContent}"`);
    }

  } catch (error) {
    const normalizedErrorMessage = normalizeError(error, modelConfig.provider, modelConfig);
    throw new HttpsError("internal", normalizedErrorMessage);
  }

  const currentTurnUserMessageEntry = { role: "user", content: userMessageContent };
  const assistantResponseEntry = { role: "assistant", content: replyText };
  const updatedConversationForDb = [...dbConversationMessages, currentTurnUserMessageEntry, assistantResponseEntry].slice(-30);

  try {
    await chatRef.set({
      messages: updatedConversationForDb,
      lastModel: modelConfig.id,
      lastUsed: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });
  } catch (dbError) {
    logger.error("Error saving chat history:", dbError);
  }

  return {
    reply: replyText,
    model: modelConfig.id,
    provider: modelConfig.provider
  };
});

exports.getChatHistory = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Authentication required.");
  const userId = request.auth.uid;
  try {
    const chatRef = db.collection("chats").doc(userId);
    const doc = await chatRef.get();
    const defaultModel = getDefaultModelId();
    if (doc.exists) {
      const data = doc.data();
      return {
        messages: data.messages || [],
        lastModel: data.lastModel || defaultModel
      };
    }
    return { messages: [], lastModel: defaultModel };
  } catch (error) {
    logger.error("Error getting chat history for user:", userId, error);
    throw new HttpsError("internal", "Failed to get chat history.");
  }
});

exports.clearChat = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Authentication required.");
  const userId = request.auth.uid;
  try {
    await db.collection("chats").doc(userId).delete();
       return { success: true };
  } catch (error) {
    logger.error("Error clearing chat for user:", userId, error);
    throw new HttpsError("internal", "Failed to clear chat history.");
  }
});
