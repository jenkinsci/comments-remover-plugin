# Comments Remover Jenkins plugin

This plugin removes comments from source files for a number of programming languages. Required Python 3 on Jenkins server.
A new build step is added: 'Invoke Comments Remover' which accepts files to process as input and creates uncommented
version of them. 

User can specify Python path in global settings for the plugin (otherwise the one on the system PATH is used).

### Before build
Put ZIP archive of Comments Remover (in Python) in `src/main/resources`.
The plugin expects to find `comment_remover.py`  and `requirement.txt` in the archive.

### Debug
`mvnDebug hpi:run`

You can attach Java Debugger to a local Java process of Jenkins.

### Prepare plugin for distribution
`mvn package`

The *.hpi file will be created in target/ directory.
To install manually on local Jenkins, copy it to $JENKINS_HOME/plugins directory.


# Global settings

Go to Manage Jenkins -> Configure System to access them

![settings](https://user-images.githubusercontent.com/9072987/30342487-630f98ce-97fa-11e7-8076-e7c02ad494f2.png "Settings")

![settings](https://user-images.githubusercontent.com/9072987/30342530-814deb06-97fa-11e7-996f-a5020d6870aa.png "Settings")

![settings](https://user-images.githubusercontent.com/9072987/30342531-816886e6-97fa-11e7-890d-d3a2837ee6f3.png "Settings")

There is help section to provide examples:

![settings](https://user-images.githubusercontent.com/9072987/30342532-8174449a-97fa-11e7-85a9-ec6d1f5239bd.png "Settings")


# Usage

The plugin can be used wherever build steps can be defined, e.g. Freestyle project

![job](https://user-images.githubusercontent.com/9072987/30342535-8179a28c-97fa-11e7-8540-25d04aad24a9.png "Job")

Adding build step:

![build](https://user-images.githubusercontent.com/9072987/30342534-817803f0-97fa-11e7-98a0-da4f43bbcf30.png "Build")

![build step](https://user-images.githubusercontent.com/9072987/30342536-817a7f86-97fa-11e7-99a5-37e9f255f0b6.png "Build step")

This part also has "help" section:

![build step help](https://user-images.githubusercontent.com/9072987/30342537-81878802-97fa-11e7-8c8c-b64f20f2fb3e.png "Build step help")

##### Example of build output and result:

![build output](https://user-images.githubusercontent.com/9072987/30342538-81903aec-97fa-11e7-92dd-e5ad839d2a4c.png "Build output")

![build result](https://user-images.githubusercontent.com/9072987/30342539-819254c6-97fa-11e7-8a65-998198cc2657.png "Build result")

![build result](https://user-images.githubusercontent.com/9072987/30342540-81930aa6-97fa-11e7-85fa-7f934169025b.png "Build result")

### Using as a shell/batch command script
Alternatively, user can create plain script build step (`Execute shell` or `Execute Windows batch command`) and run `comments_remover.py` Python script
manually.

![shell step](https://user-images.githubusercontent.com/9072987/30379725-bf93011a-9897-11e7-992a-874f7880d5c5.jpg "Run shell build step")

Comments Remover script is unpacked to JENKINS_HOME directory (which is available in script as environmental variable)

![shell step output](https://user-images.githubusercontent.com/9072987/30379735-c522fc02-9897-11e7-9286-c3363f1c4a2b.jpg "Execute shell build step output")

