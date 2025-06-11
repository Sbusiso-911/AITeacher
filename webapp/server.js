const express = require('express');
const fs = require('fs');
const path = require('path');
const cors = require('cors');
const bodyParser = require('body-parser');

const app = express();
const PORT = process.env.PORT || 3000;
const DATA_FILE = path.join(__dirname, 'conversations.json');

app.use(cors());
app.use(bodyParser.json());
app.use(express.static(path.join(__dirname, 'public')));

let conversations = {};

// Load existing data if file exists
if (fs.existsSync(DATA_FILE)) {
  try {
    conversations = JSON.parse(fs.readFileSync(DATA_FILE));
  } catch (err) {
    console.error('Error reading data file', err);
  }
}

function saveData() {
  fs.writeFileSync(DATA_FILE, JSON.stringify(conversations, null, 2));
}

app.get('/conversations/:id', (req, res) => {
  const { id } = req.params;
  const convo = conversations[id] || [];
  res.json(convo);
});

app.post('/conversations/:id/messages', (req, res) => {
  const { id } = req.params;
  const message = req.body;
  if (!conversations[id]) conversations[id] = [];
  conversations[id].push({
    ...message,
    timestamp: Date.now()
  });
  saveData();
  res.status(201).json({ status: 'ok' });
});

app.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
});
