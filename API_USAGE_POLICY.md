# API Usage Policy

This project follows these best practices for handling API keys derived from the OpenAI documentation:

1. **Use Individual API Keys**
   - Each team member must use a unique API key. Do not share keys between users.

2. **Avoid Client-Side Exposure**
   - API keys must never be embedded in client-side code such as browsers or mobile apps. All API requests should be made from a secure backend service.

3. **Keep Keys Out of the Repository**
   - Never commit API keys to this repository. Use environment variables or a secure key management service instead.

4. **Store Keys in Environment Variables**
   - Configure your operating system to store API keys in an environment variable named `OPENAI_API_KEY`. Reference this variable in code rather than hard-coding the key.

5. **Consider a Key Management Service**
   - For production deployments, use a dedicated key management service to securely store and rotate API keys.

6. **Monitor and Rotate Keys**
   - Regularly monitor API usage for anomalies and rotate keys if you suspect they are compromised.

By adhering to these rules, we keep our API credentials secure and reduce the risk of unauthorized access.
