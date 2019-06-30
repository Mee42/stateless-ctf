package com.gumbocoin

import com.codahale.shamir.Scheme
import com.google.gson.Gson
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import java.security.SecureRandom
import javax.xml.bind.DatatypeConverter
import kotlin.random.Random
import javax.crypto.spec.IvParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec








val CTF_BUILD_DIR = File("build/")
val QUESTION_BUILD_DIR = File(CTF_BUILD_DIR,"questions")
val ENCRYPTION_BUILd_DIR = File(CTF_BUILD_DIR,"encryption")

val QUESTION_DIR = File("files/questions/")

val TEAM_DIR = File("files/teams")

const val CTF_HEADER = "ctf{"
const val CTF_ENDER = "}"


fun main() {
    CTF_BUILD_DIR.deleteRecursively()

    val teams = readTeams()

    val questions = readQuestions()
    println("Number of questions:${questions.size}")

    teams.forEach { team ->
        val compiledQuestions = questions.map { compileQuestion(it,team) }
        val tar = stepOneEncrypt(compiledQuestions,team,2)
        //encrypt with gpg
//        val gpg = File(tar.parentFile,tar.name + ".gpg")

        val makeExec = ProcessBuilder("gpg","--encrypt","--recipient-file",team.gpg.absolutePath,tar.absolutePath)
        makeExec.directory(ENCRYPTION_BUILd_DIR)
        makeExec.inheritIO()
        makeExec.start().waitFor()
    }

}

fun flagHashToBinary(flag :String):ByteArray =
        flag.substring(CTF_HEADER.length,flag.length - CTF_ENDER.length)
        .let { DatatypeConverter.parseHexBinary(it) }

const val LEAVE_TMP = false
//need to delete before exporting,MAKE SURE this is false when in production.



//returns the output
fun stepOneEncrypt(compiledQuestions: List<CompiledQuestion>, team: Team, unlockedd :Int) :File {
    val build = File(ENCRYPTION_BUILd_DIR,team.name)
    build.mkdirs()
    if(compiledQuestions.size < unlockedd){
        error("Unlocked ($unlockedd) can not be bigger the the compiledQuestions size${compiledQuestions.size}")
    }

    val toDeleteBeforePackaging = mutableListOf<File>()
    val unlocked = unlockedd - 2
    for(i in 0 until compiledQuestions.size){//do this to all questions
        val question = compiledQuestions[i]
        val questionDir = File(build,question.name)
        questionDir.mkdir()
        File(questionDir,"desc.txt").writeText(question.desc,Charsets.UTF_8)

        val tmpFileDir = File(questionDir,"tmp")
        tmpFileDir.mkdir()
        for(file in question.files){
            File(tmpFileDir,file.name).writeBytes(file.content)
        }
        val tarFile = File(questionDir,"files.tar.gz")
        val tarrer = ProcessBuilder("bash","-c","tar czf ${tarFile.name} tmp/*")
        tarrer.inheritIO()
        tarrer.directory(questionDir)
        tarrer.start().waitFor()
        toDeleteBeforePackaging += tmpFileDir


        //the ones that need to be locked
        if(i > (unlocked + 1)){
            println("Using $")
            //we need to add the keys (after encrypting them with questions[0 until i] flags)
            //then we need to encrypt the tar.gz file.
            //and store it back
            val keydir = File(questionDir,"keys")
            keydir.mkdirs()

            println("i:$i,unlocked:$unlocked,k:${i - unlocked}")
            val scheme = Scheme(SecureRandom(),i,i - unlocked)

            val secret = tarFile.readBytes()

            val splitSecret = scheme.split(secret)
                    .toList()
                    .map { it.second }//the order doesn't matter.
            for(q in 0 until i){
                val subQuestion = compiledQuestions[q]
                val name = subQuestion.name
                val key = flagHashToBinary(subQuestion.key)//gets the raw number of the hash. correctly padded key for AES
                val value = splitSecret[q]
                val fileContent = Gson().toJson(aesEncrypt(input = value,key = key))
                val file = File(keydir, "$name.key.enc")
                file.writeText(fileContent)
            }
            toDeleteBeforePackaging += tarFile//DELETE THE TAR FILE

        }

    }
    toDeleteBeforePackaging.forEach {
        if(LEAVE_TMP){
            println("Renaming $it to ${File(it.parentFile.absolutePath,it.name + ".deleted")}")
            it.renameTo(File(it.parentFile.absolutePath,it.name + ".deleted"))
        }else{
            val correct = if(it.isDirectory) it.deleteRecursively() else it.delete()
            if(!correct) error("Deletion of $it did not succeed")
        }
    }
    //tar everything..? yes
    val tarFile = File(ENCRYPTION_BUILd_DIR,team.name + ".tar.gz")
    val tarrer = ProcessBuilder("bash","-c","tar czf ${tarFile.name} ${team.name}/*")
    tarrer.inheritIO()
    tarrer.directory(ENCRYPTION_BUILd_DIR)
    tarrer.start().waitFor()

    return tarFile
}



fun aesEncrypt(input :ByteArray, key :ByteArray):AESEncryptedBytes{
    val iv = Random.nextBytes(16)
    val ivSpec = IvParameterSpec(iv)
    val keySpec = SecretKeySpec(key, "AES")

    val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
    return AESEncryptedBytes(iv = iv, content = cipher.doFinal(input))
}

fun aesDecrypt(bytes: AESEncryptedBytes, key :ByteArray):ByteArray{
    val ivSpec = IvParameterSpec(bytes.ivBytes)
    val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    val keySpec = SecretKeySpec(key,"AES")
    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
    return cipher.doFinal(bytes.contentBytes)
}

//base64, for easy serialization
class AESEncryptedBytes(
        private val iv :String,
        private val content :String){

    constructor(iv :ByteArray,content :ByteArray):this(
            iv = iv.toBase64String(),
            content = content.toBase64String()
    )

    val ivBytes :ByteArray by lazy { iv.toByteArrayBase64() }
    val contentBytes :ByteArray by lazy { content.toByteArrayBase64() }
}

fun ByteArray.toBase64String():String = Base64.encodeBase64String(this)
fun String.toByteArrayBase64():ByteArray = Base64.decodeBase64(this)

fun readQuestions():List<Question>{
    return QUESTION_DIR.listFiles()!!.map {  questionDir ->
        val name = questionDir.name.substring(0,questionDir.name.lastIndexOf("_"))
        val index = questionDir.name.substring(questionDir.name.lastIndexOf("_") + 1).toInt()
        val desc = File(questionDir,"desc.txt").readText(Charsets.UTF_8)
        val producerFiles = File(questionDir,"files").listFiles()!!.map { producerFile ->
            ProducerFile(
                    name = producerFile.name,
                    producer = producerFile.readLines(Charsets.UTF_8)
            )
        }
        val deps = File(questionDir,"deps").listFiles()?.map {
            DependencyFile(name = it.name, content = it.readBytes())
        } ?: emptyList()

        val keyfile = File(questionDir,"key.bin")
        val key = if(!keyfile.exists()){
            val x = Random.Default.nextBytes(16)
            keyfile.writeBytes(x)
            x
        }else { keyfile.readBytes() }

        Question(
                name = name,
                desc = desc,
                index = index,
                producers = producerFiles,
                deps = deps,
                key = key)
    }.sortedBy { it.index }
}

fun compileQuestion(question: Question, team :Team): CompiledQuestion {

    val build = File(File(QUESTION_BUILD_DIR,team.name),question.name)
    println("Building question ${question.name} in directory ${build.absolutePath}")
    build.mkdirs()

    val buildFilesDir = File(build,"buildfiles")
    buildFilesDir.mkdirs()

    val exportFiles = File(build,"files")
    exportFiles.mkdirs()

    //generate the proper keyfile

    //generate the hex..?
    val key = CTF_HEADER + DigestUtils.sha256Hex(question.key + team.token) + CTF_ENDER
    val keyfile = File(build,"key.txt")

    keyfile.writeText(key,Charsets.UTF_8)

    val preparedFiles = mutableListOf<PreparedFile>()

    for(producer in question.producers){

        val buildDir = File(buildFilesDir,producer.name)
        buildDir.mkdir()
        for(dep in question.deps){
            File(buildDir,dep.name).writeBytes(dep.content)
        }
        val runner = File(buildDir,producer.name)
        runner.writeText(producer.producer.fold(""){a,b -> "$a\n$b"} )

        val makeExec = ProcessBuilder("chmod","+x",runner.absolutePath)
        makeExec.directory(buildDir)
        makeExec.inheritIO()
        makeExec.start().waitFor()

        val pb = ProcessBuilder("bash","-c","${runner.name} \"${keyfile.absolutePath}\"")
        val real = File(exportFiles,producer.name)
        pb.redirectOutput(real)
        pb.directory(buildDir)
        val process = pb.start()
        process.waitFor()
        preparedFiles.add(PreparedFile(name = producer.name, content = real.readBytes()))
    }

    return CompiledQuestion(question.name,question.desc,preparedFiles,key)
}


fun readTeams():List<Team>{
    return TEAM_DIR.listFiles()!!.map { teamDir ->
        val name = teamDir.name
        val tokenFile = File(teamDir,"token.bin")
        val token = if(tokenFile.exists()){
            tokenFile.readBytes()
        }else {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            tokenFile.writeBytes(bytes)
            bytes
        }
        val gpg = File(teamDir,"publickey.txt")
        if(!gpg.exists())
            error("Can't find gpg file for team $name")
        Team(name = name,token = token,gpg = gpg)
    }
}

class Team(val name :String,
           val token :ByteArray,
           val gpg :File)


data class ProducerFile(
        val name :String,
        val producer :List<String>)


class DependencyFile(
        val name :String,
        val content :ByteArray)

class PreparedFile(
        val name :String,
        val content :ByteArray)

class Question(val name :String,
               val desc :String,
               val index :Int,
               val producers :List<ProducerFile>,
               val deps :List<DependencyFile>,
               val key :ByteArray)


class CompiledQuestion(
        val name :String,
        val desc :String,
        val files :List<PreparedFile>,
        val key :String)