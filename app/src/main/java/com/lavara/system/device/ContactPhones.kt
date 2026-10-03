package com.lavara.system.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Elegir un número de la agenda sin pedir el permiso de contactos: Android abre su propia lista y solo le
 * pasa a La Vara el número que el usuario tocó.
 */
object ContactPhones {

    fun pickIntent(): Intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    /** Número y nombre del contacto elegido, o null si no se pudo leer. */
    fun read(context: Context, uri: Uri): Pair<String, String>? = try {
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null else (cursor.getString(0) ?: "") to (cursor.getString(1) ?: "")
        }
    } catch (e: RuntimeException) {
        null
    }

    /**
     * El número con código de país ("+50688887777"), usando el país de la SIM cuando la agenda lo tiene
     * sin código ("8888 7777"). Si no se puede saber, lo devuelve como estaba.
     */
    fun withCountryCode(context: Context, number: String): String {
        if (number.trim().startsWith("+")) return number
        val tm = context.getSystemService(TelephonyManager::class.java)
        val country = listOfNotNull(tm?.simCountryIso, tm?.networkCountryIso, Locale.getDefault().country)
            .firstOrNull { it.isNotBlank() } ?: return number
        return PhoneNumberUtils.formatNumberToE164(number, country.uppercase(Locale.ROOT)) ?: number
    }
}
