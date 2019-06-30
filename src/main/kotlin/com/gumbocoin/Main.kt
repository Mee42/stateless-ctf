package com.gumbocoin

import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import kotlin.random.Random


val CTF_BUILD_DIR = File("build/ctf/")
val QUESTION_DIR = File("questions/")

const val CTF_HEADER = "ctf{"
const val CTF_ENDER = "}"

fun main() {

    val team1 = Team(
            name = "team1",
            token = Random.Default.nextBytes(2))
    val team2 = Team(
            name = "team2",
            token = Random.Default.nextBytes(2))
    val teams = listOf(team1,team2)
    CTF_BUILD_DIR.deleteRecursively()

    readQuestions()
            .forEach { question ->
                teams.forEach { team ->
                    compileQuestion(question,team)
                }
            }
}


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
        val deps = File(questionDir,"deps").listFiles()!!.map {
            DependencyFile(name = it.name, content = it.readBytes())
        }

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
    }
}

fun compileQuestion(question: Question, team :Team): CompiledQuestion {

    val build = File(File(CTF_BUILD_DIR,team.name),question.name)
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

    return CompiledQuestion(question.name,question.desc,preparedFiles)
}


class Team(val name :String,
           val token :ByteArray)


/**
 * @param producer is a shell script that accepts one argument, the name of the key file to use
 * And outputs the file into stdout. It can create whatever files it wants in the home directory, and
 * files specified in dependecies will also be there
 */

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
        val files :List<PreparedFile>)