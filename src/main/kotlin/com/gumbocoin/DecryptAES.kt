package com.gumbocoin

import com.codahale.shamir.Scheme
import com.google.gson.Gson
import java.io.File
import java.security.SecureRandom

fun <T> fillThisInYourself():T = error("")

fun howToDecryptAESFiles() {
    val encryptedFile : File = fillThisInYourself()
    val key :String = fillThisInYourself()//this is the flag, in the $CTF_HEADER $HASH $CTF_ENDER format

    val aes = Gson().fromJson(key,AESEncryptedBytes::class.java)
    val result = aesDecrypt(aes, flagHashToBinary(key))
}


fun howToDecryptSSS(){
    val keys = fillThisInYourself<Map<Int,ByteArray>>()//these keys are labeled question.key.enc and encrypted with AES
    //the int is the index, the first question has an index of 0 and so on. Not all indexes must be used
    val i = fillThisInYourself<Int>()//this is (or is one off of) the number of keys
    val scheme = Scheme(SecureRandom(),i,i - 1)
    val output = scheme.join(keys)
    val outputFile = fillThisInYourself<File>()//the file to write the bytes to
    outputFile.writeBytes(output)
}

