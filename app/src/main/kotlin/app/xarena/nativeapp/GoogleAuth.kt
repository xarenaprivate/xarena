package app.xarena.nativeapp

import android.app.Activity

/** Native Google sign-in via Credential Manager (no browser). The Google ID token is exchanged
    with Supabase (provider google) in Api.loginWithGoogle. Requires the web OAuth client ID in
    site_settings key "native" {google_web_client_id} and an Android OAuth client for this app. */
object GoogleAuth {
    suspend fun signIn(activity: Activity): String {
        val serverId = RemoteConfig.googleWebClientId()
        if (serverId.isBlank()) throw Api.ApiError("Google sign-in is being set up - use phone or email for now.")
        val cm = androidx.credentials.CredentialManager.create(activity)
        val opt = androidx.credentials.googleid.GetGoogleIdOption.Builder()
            .setServerClientId(serverId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val req = androidx.credentials.GetCredentialRequest.Builder().addCredentialOption(opt).build()
        val res = cm.getCredential(activity, req)
        val cred = androidx.credentials.googleid.GoogleIdTokenCredential.createFrom(res.credential.data)
        return cred.idToken
    }
}
