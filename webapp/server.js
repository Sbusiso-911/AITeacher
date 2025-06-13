const express = require('express');
const fs = require('fs');
const path = require('path');
const app = express();
const PORT = process.env.PORT || 3000;

const dataDir = path.join(__dirname, 'data');
if (!fs.existsSync(dataDir)) {
  fs.mkdirSync(dataDir);
}

app.use(express.json({ limit: '2mb' }));

// POST /api/sync { user_id, conversation_id, messages }
app.post('/api/sync', (req, res) => {
  const { user_id, conversation_id, messages } = req.body;
  if (!user_id || !conversation_id || !Array.isArray(messages)) {
    return res.status(400).json({ error: 'Invalid payload' });
  }
  const userFile = path.join(dataDir, `${user_id}.json`);
  let userData = {};
  if (fs.existsSync(userFile)) {
    try { userData = JSON.parse(fs.readFileSync(userFile, 'utf8')); } catch (e) { }
  }
  userData[conversation_id] = messages;
  fs.writeFileSync(userFile, JSON.stringify(userData));
  res.json({ status: 'ok' });
});

// GET /api/history?user_id=XXX
app.get('/api/history', (req, res) => {
  const userId = req.query.user_id;
  if (!userId) return res.status(400).json({ error: 'user_id required' });
  const userFile = path.join(dataDir, `${userId}.json`);
  if (!fs.existsSync(userFile)) {
    return res.json([]);
  }
  try {
    const data = JSON.parse(fs.readFileSync(userFile, 'utf8'));
    // Flatten to just latest conversation messages? We'll merge all
    const allMessages = [];
    Object.keys(data).forEach(cid => { allMessages.push(...data[cid]); });
    res.json(allMessages);
  } catch (e) {
    res.status(500).json({ error: 'Failed to read history' });
  }
});

app.listen(PORT, () => {
  console.log(`AITeacher web app listening on port ${PORT}`);
});
