const admin = require('firebase-admin');

// Prepare OpenAI mock instance
const openaiInstance = {
  chat: {
    completions: {
      create: jest.fn().mockResolvedValue({
        choices: [{ message: { content: 'mock reply' } }]
      })
    }
  }
};

jest.mock('openai', () => {
  return { OpenAI: jest.fn(() => openaiInstance) };
});

describe('chat cloud function', () => {
  let chat;
  let messages;
  let docMock;

  beforeAll(() => {
    process.env.FUNCTIONS_EMULATOR = 'true';
    process.env.OPENAI_KEY = 'test-key';
    messages = [];
    docMock = {
      get: jest.fn().mockImplementation(() => Promise.resolve({
        exists: messages.length > 0,
        data: () => ({ messages: [...messages] })
      })),
      set: jest.fn().mockImplementation(data => {
        messages = [...data.messages];
        return Promise.resolve();
      }),
      delete: jest.fn()
    };

    const firestoreMock = {
      collection: jest.fn().mockReturnValue({
        doc: jest.fn().mockReturnValue(docMock)
      })
    };

    jest.spyOn(admin, 'firestore').mockReturnValue(firestoreMock);
    jest.spyOn(admin, 'initializeApp').mockImplementation(() => {});

    chat = require('./index').chat;
  });

  beforeEach(() => {
    messages.length = 0;
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  test('stores no more than six messages after replying', async () => {
    for (let i = 1; i <= 3; i++) {
      messages.push({ role: 'user', content: `u${i}` });
      messages.push({ role: 'assistant', content: `a${i}` });
    }

    const context = { auth: { uid: 'user1' } };
    await chat({ message: 'hello' }, context);

    expect(messages.length).toBeLessThanOrEqual(6);
    expect(messages[0].content).toBe('u2');
    expect(messages[messages.length - 1]).toEqual({
      role: 'assistant',
      content: 'mock reply'
    });
  });
});
