import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object Security {
    private const val TAG = "Security"

    fun verifyPurchase(base64PublicKey: String, signedData: String, signature: String): Boolean {
        return try {
            val keyFactory = KeyFactory.getInstance("RSA")
            val keySpec = X509EncodedKeySpec(Base64.decode(base64PublicKey, Base64.DEFAULT))
            val publicKey = keyFactory.generatePublic(keySpec)

            val sig = Signature.getInstance("SHA1withRSA")
            sig.initVerify(publicKey)
            sig.update(signedData.toByteArray())

            if (!sig.verify(Base64.decode(signature, Base64.DEFAULT))) {
                Log.e(TAG, "Signature verification failed.")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying purchase", e)
            false
        }
    }
}