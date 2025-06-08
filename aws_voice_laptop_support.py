import boto3
import json
import base64
import io
import wave
import pyaudio
from contextlib import contextmanager

class AWSVoiceLaptopSupport:
    def __init__(self):
        # AWS clients
        self.bedrock_runtime = boto3.client('bedrock-runtime', region_name='us-west-2')
        self.transcribe = boto3.client('transcribe', region_name='us-west-2')
        self.polly = boto3.client('polly', region_name='us-west-2')
        
        # Audio settings
        self.audio_format = pyaudio.paInt16
        self.channels = 1
        self.rate = 16000
        self.chunk = 1024
        
        self.conversation_history = []
    
    def record_audio(self, duration=5):
        """Record audio from microphone"""
        audio = pyaudio.PyAudio()
        
        print(f"🎤 Recording for {duration} seconds...")
        
        stream = audio.open(
            format=self.audio_format,
            channels=self.channels,
            rate=self.rate,
            input=True,
            frames_per_buffer=self.chunk
        )
        
        frames = []
        for _ in range(0, int(self.rate / self.chunk * duration)):
            data = stream.read(self.chunk)
            frames.append(data)
        
        stream.stop_stream()
        stream.close()
        audio.terminate()
        
        audio_data = io.BytesIO()
        with wave.open(audio_data, 'wb') as wf:
            wf.setnchannels(self.channels)
            wf.setsampwidth(audio.get_sample_size(self.audio_format))
            wf.setframerate(self.rate)
            wf.writeframes(b''.join(frames))
        
        return audio_data.getvalue()
    
    def transcribe_audio_realtime(self, audio_data):
        """Transcribe audio using AWS Transcribe (streaming or batch)"""
        try:
            audio_base64 = base64.b64encode(audio_data).decode('utf-8')
            import speech_recognition as sr
            recognizer = sr.Recognizer()
            audio_file = io.BytesIO(audio_data)
            with sr.AudioFile(audio_file) as source:
                audio = recognizer.record(source)
            text = recognizer.recognize_google(audio)
            return text
        except Exception as e:
            print(f"Transcription error: {e}")
            return None
    
    def synthesize_speech_aws(self, text):
        """Convert text to speech using Amazon Polly"""
        try:
            response = self.polly.synthesize_speech(
                Text=text,
                OutputFormat='mp3',
                VoiceId='Joanna',
                Engine='neural',
                LanguageCode='en-US'
            )
            audio_stream = response['AudioStream']
            audio_data = audio_stream.read()
            with open('/tmp/response.mp3', 'wb') as f:
                f.write(audio_data)
            import os, platform
            system = platform.system()
            if system == 'Darwin':
                os.system('afplay /tmp/response.mp3')
            elif system == 'Linux':
                os.system('mpg123 /tmp/response.mp3')
            elif system == 'Windows':
                os.system('start /tmp/response.mp3')
            return True
        except Exception as e:
            print(f"Speech synthesis error: {e}")
            return False
    
    def get_laptop_support_response(self, user_question):
        """Get response from Claude via Bedrock"""
        system_prompt = (
            "You are an expert laptop technical support specialist providing voice support. "
            "Keep responses conversational and concise (1-2 sentences for simple issues). "
            "Use natural speech patterns and avoid technical jargon when possible."
        )
        messages = []
        if self.conversation_history:
            messages.extend(self.conversation_history[-6:])
        messages.append({"role": "user", "content": user_question})
        request_body = {
            "anthropic_version": "bedrock-2023-05-31",
            "max_tokens": 600,
            "temperature": 0.3,
            "system": system_prompt,
            "messages": messages,
        }
        try:
            response = self.bedrock_runtime.invoke_model(
                modelId="anthropic.claude-3-haiku-20240307-v1:0",
                body=json.dumps(request_body)
            )
            response_body = json.loads(response['body'].read())
            answer = response_body['content'][0]['text']
            self.conversation_history.extend([
                {"role": "user", "content": user_question},
                {"role": "assistant", "content": answer},
            ])
            return answer
        except Exception:
            return "I'm having trouble accessing my support database. Please try again."
    
    def voice_interaction_loop(self):
        print("🎤 AWS VOICE LAPTOP SUPPORT")
        print("=" * 50)
        greeting = "Hello! I'm your voice-enabled laptop support specialist. How can I help you today?"
        print(f"🔊 Assistant: {greeting}")
        self.synthesize_speech_aws(greeting)
        while True:
            try:
                print("\n" + "="*30)
                print("Options:")
                print("1. Press Enter and speak (5 seconds)")
                print("2. Extended recording (10 seconds)")
                print("3. Exit")
                choice = input("\nSelect option or press Enter to speak: ").strip()
                if choice == '3' or choice.lower() in ['exit','quit']:
                    farewell = "Thank you for using voice laptop support. Have a great day!"
                    print(f"🔊 Assistant: {farewell}")
                    self.synthesize_speech_aws(farewell)
                    break
                duration = 10 if choice == '2' else 5
                audio_data = self.record_audio(duration)
                print("🔄 Processing your speech...")
                user_speech = self.transcribe_audio_realtime(audio_data)
                if user_speech:
                    print(f"📝 You said: {user_speech}")
                    if any(p in user_speech.lower() for p in ['goodbye','exit','quit','stop']):
                        farewell = 'Goodbye! Feel free to come back anytime you need laptop support.'
                        print(f"🔊 Assistant: {farewell}")
                        self.synthesize_speech_aws(farewell)
                        break
                    response = self.get_laptop_support_response(user_speech)
                    print(f"🔊 Assistant: {response}")
                    if not self.synthesize_speech_aws(response):
                        print("🔇 Text-to-speech failed, but here's the written response above.")
                else:
                    error_msg = "I didn't catch that. Could you please speak more clearly?"
                    print(f"🔊 Assistant: {error_msg}")
                    self.synthesize_speech_aws(error_msg)
            except KeyboardInterrupt:
                print("\n\n👋 Session ended by user")
                break
            except Exception as e:
                print(f"❌ Error: {e}")
                continue

def main():
    print("🚀 AWS VOICE-ENABLED LAPTOP SUPPORT")
    print("=" * 60)
    try:
        boto3.client('bedrock-runtime', region_name='us-west-2')
        boto3.client('polly', region_name='us-west-2')
        print("✅ AWS services connected successfully")
        voice_support = AWSVoiceLaptopSupport()
        voice_support.voice_interaction_loop()
    except Exception as e:
        print(f"❌ Initialization error: {e}")
        print("Please check your AWS credentials and region settings.")

if __name__ == "__main__":
    main()
