from flask import Flask, request, jsonify, render_template
from openai import OpenAI
import os
import requests

app = Flask(__name__)

# Initialize the OpenAI client
api_key = os.getenv('OPENAI_API_KEY')
if not api_key:
    raise ValueError("OpenAI API key not found. Please set the OPENAI_API_KEY environment variable.")
client = OpenAI(api_key=api_key)

@app.route("/")
def home():
    return render_template("index.html")

@app.route("/chat", methods=["POST"])
def chat():
    user_input = request.json.get("message")
    try:
        response = client.chat.completions.create(
            model="gpt-4",
            messages=[
                {"role": "user", "content": user_input}
            ]
        )
        return jsonify({"reply": response.choices[0].message.content})
    except Exception as e:
        print('Error:', e)
        return jsonify({"error": "Unable to get response from OpenAI"}), 500

@app.route("/chat-history", methods=["GET"])
def chat_history():
    # Load chat history from a file or database
    # For simplicity, we'll return a static example
    chat_history = [
        {"text": "Hello, how can I help you?", "className": "bot-message"},
        {"text": "What is the weather like today?", "className": "user-message"}
    ]
    return jsonify(chat_history)

if __name__ == "__main__":
    # Check network connectivity
    try:
        response = requests.get('https://api.openai.com')
        if response.status_code != 200:
            raise ConnectionError("Unable to reach OpenAI API. Status code: {}".format(response.status_code))
    except Exception as e:
        print('Network Error:', e)
        raise

    app.run(host="0.0.0.0", port=5000)