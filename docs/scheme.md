
# A scheme for a stateless capture-the-flag challenge

The idea is that all files can be generated and distributed to teams
dynamically, while keeping the flags different per-team and maintaining
a heap question progression.


Question directory structure:


There is one input variable, **n**, which is the number of questions
to start unlocked.
```
files/questions
  |
  |
  |- sampleA_1(the name, underscore(_), the number)
  |     |- desc.txt (the description)
  |     |- files
  |     |    |- file1.txt (the producer for file1.txt,run with bash)
  |     |    |-  ~etc~
  |     |- deps
  |     |    |- dep.txt (dependency of a prod script)
  |     |    |- ~etc~
  |     |- key.bin (the keyfile - will be created if it does not exist)
  |- ~etc~     
```
Teams tokens are also stored in a directory
```
files/teams
 |- team1
 |   |- token.bin (raw binary)
 |   |- publickey.txt (their public key)
 |- team2
 |   |- token.bin (raw binary)
 |- ~etc~
```        
Build directory structure
```
build/questions
          |
          |- team1 (the team name)
          |   |
          |   |
          |   |- question1(the name)
          |   |     |- buildfiles
          |   |     |     |- file1.txt (name of file)
          |   |     |     |    |- file1.txt (the actual shell script)
          |   |     |     |    |- dep.txt (a dependency)
          |   |     |     |    |- ~etc~ (more dependencies)
          |   |     |     |- ~etc~
          |   |     |- files
          |   |     |     |- file1.txt (the produced file)
          |   |     |     |- ~etc~
          |   |     |- key.bin (the team-specific keyfile)
          |   |- ~etc~ (more questions)
          |- ~etc~ (more teams)
  
```  
After the question is built, it goes through an encryption phase

That results in this directory structure:
```
build/encryption
          |
          |- team1.tar.gz (the compressed team1/ directory)
          |- team1.tar.gz.gpg (the encrypted file)
          |- team1 (the team name)
          |   | 
              |- question1_1(the name)
              |     |- desc.txt (the description)
              |     |- files.tar.gz (no password, compressed files)
              |- ~etc~ (the first **n**(3 here) files are not encrypted)
              |
              |- question4_4(the name)
              |     |- desc.txt (the description)
              |     |- keys
              |     |    |- question1_1.key.enc (question1_1.key encrypted with the question one key. AES)
              |     |    |- question2_2.key.enc
              |     |    |- question3_3.key.enc 
              |     |- files.tar.gz.enc (files encrypted with SSS, needs 1(the question number minus **n**) key to open)
              |- question5_5(the name)
              |     |- desc.txt (the description)
              |     |- keys
              |     |    |- question1_1.key.enc
              |     |    |- question2_2.key.enc
              |     |    |- question3_3.key.enc
              |     |    |- question4_4.key.enc
              |     |- files.tar.gz.enc (SSS encryption, need 2(the question number minus **n**) keys to open)
              |- question6_6
              |     |- desc.txt (the description)
              |     |- keys
              |     |    |- question1_1.key.enc
              |     |    |- question2_2.key.enc
              |     |    |- question3_3.key.enc
              |     |    |- question4_4.key.enc
              |     |    |- question5_5.key.enc
              |     |- files.tar.gz.enc (SSS encryption, need 6 - **n** keys to open)
```
The team1/ directory is compressed then encrypted with gpg public/private key encryption,
and `team1.tar.gz.gpg` is uploaded to github.