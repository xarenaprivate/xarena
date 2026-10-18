package app.xarena.nativeapp

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPairGenerator
import javax.crypto.KeyAgreement
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.math.BigInteger
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/* End-to-end DM encryption, matching the site's WebCrypto flow (assets/js/dm.js):
   ECDH P-256 keypair per device, public key published as JWK to dm_public_keys,
   thread key = raw ECDH shared secret as AES-GCM-256 key, 12-byte IV per message. */
object DmCrypto {
    private var priv: java.security.PrivateKey? = null
    private var pub: java.security.PublicKey? = null
    private val aesCache = HashMap<String, SecretKeySpec>()

    private fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
    private fun b64url(b: ByteArray): String = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun unb64url(s: String): ByteArray = Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    @Synchronized
    fun ensureKeys() {
        if (priv != null && pub != null) return
        val p = Api.ctx.getSharedPreferences("dm_keys", 0)
        val pkcs8 = p.getString("priv", null)
        val x509 = p.getString("pub", null)
        if (pkcs8 != null && x509 != null) {
            try {
                val kf = KeyFactory.getInstance("EC")
                priv = kf.generatePrivate(PKCS8EncodedKeySpec(unb64(pkcs8)))
                pub = kf.generatePublic(X509EncodedKeySpec(unb64(x509)))
                return
            } catch (e: Exception) { /* regenerate below */ }
        }
        val g = KeyPairGenerator.getInstance("EC")
        g.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        val kp = g.generateKeyPair()
        priv = kp.private; pub = kp.public
        p.edit().putString("priv", b64(kp.private.encoded)).putString("pub", b64(kp.public.encoded)).apply()
    }

    /* JWK like WebCrypto exportKey("jwk", publicKey): {kty:"EC",crv:"P-256",x,y} base64url, 32-byte coords */
    private fun fixed32(v: BigInteger): ByteArray {
        var b = v.toByteArray()
        if (b.size > 32 && b[0].toInt() == 0) b = b.copyOfRange(1, b.size)
        if (b.size < 32) { val out = ByteArray(32); System.arraycopy(b, 0, out, 32 - b.size, b.size); return out }
        return b
    }
    fun myPublicJwk(): JSONObject {
        ensureKeys()
        val ec = pub as java.security.interfaces.ECPublicKey
        return JSONObject()
            .put("kty", "EC").put("crv", "P-256")
            .put("x", b64url(fixed32(ec.w.affineX)))
            .put("y", b64url(fixed32(ec.w.affineY)))
    }

    private fun importPeerPub(jwk: JSONObject): java.security.PublicKey {
        val x = BigInteger(1, unb64url(jwk.getString("x")))
        val y = BigInteger(1, unb64url(jwk.getString("y")))
        val params = java.security.AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
        val spec = params.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), spec))
    }

    /* Raw ECDH shared secret (X coordinate, 32 bytes) used directly as the AES key - WebCrypto deriveKey behavior. */
    fun threadKey(peerId: String, peerJwk: JSONObject): SecretKeySpec {
        aesCache[peerId]?.let { return it }
        ensureKeys()
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv)
        ka.doPhase(importPeerPub(peerJwk), true)
        var secret = ka.generateSecret()
        if (secret.size < 32) { val out = ByteArray(32); System.arraycopy(secret, 0, out, 32 - secret.size, secret.size); secret = out }
        val key = SecretKeySpec(secret, "AES")
        aesCache[peerId] = key
        return key
    }

    data class Enc(val ciphertext: String, val iv: String)

    fun encrypt(key: SecretKeySpec, plain: ByteArray): Enc {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return Enc(b64(c.doFinal(plain)), b64(iv))
    }
    fun decrypt(key: SecretKeySpec, ciphertext: String, iv: String): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, unb64(iv)))
        return c.doFinal(unb64(ciphertext))
    }
}
