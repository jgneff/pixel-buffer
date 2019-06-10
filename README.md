# PixelBuffer Tests

This project tests the new PixelBuffer class proposed by the pull request [ javafxports/openjdk-jfx#472](https://github.com/javafxports/openjdk-jfx/pull/472), "JDK-8167148 : Add native rendering support by supporting WritableImages backed by NIO ByteBuffers."

## Licenses

The content of this project is licensed under the [GNU General Public License v3.0](https://choosealicense.com/licenses/gpl-3.0/) except for the following:

* The [duke-waving.gif](src/duke-waving.gif) file is licensed under the [Creative Commons Attribution-ShareAlike 4.0 International License](https://choosealicense.com/licenses/cc-by-sa-4.0/).
* The [Renoir_by_Bazille.jpg](src/Renoir_by_Bazille.jpg) file is in the public domain and was downloaded from [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Renoir_by_Bazille.jpg).

## Building

The application is a project of the [Apache NetBeans IDE](https://netbeans.apache.org/) with the following settings:

* the Java platform set to the default (JDK 12),
* the source and binary format set to JDK 12, and
* a global library named "JavaFX 13" that contains the JavaFX SDK built with the [pull request branch](https://github.com/javafxports/openjdk-jfx/pull/472).

## Running

Run the Animator application with a command like the following:

```ShellSession
$ $HOME/opt/jdk-12.0.1+12/bin/java --add-modules=javafx.graphics \
    --module-path=$HOME/lib/javafx-sdk-13-dev/lib \
    -Dprism.order=sw -Djavafx.animation.pulse=2 Animator
```

Change the line in the source code to select either `animationOld` or `animationNew`:

```Java
animation = animationNew;
```

Run the Viewer application with a command like the following:

```ShellSession
$ $HOME/opt/jdk-12.0.1+12/bin/java --add-modules=javafx.graphics \
    --module-path=$HOME/lib/javafx-sdk-13-dev/lib Viewer
```

Change the lines in the source code to select the method used to convert the AWT image into a JavaFX image:

```Java
//        oldDraw();
//        oldCopy();
//        newDraw();
        newCopy();
```
