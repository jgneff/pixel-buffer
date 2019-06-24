# PixelBuffer Tests

This project tests the new `PixelBuffer` class proposed by the pull request [javafxports/openjdk-jfx#472](https://github.com/javafxports/openjdk-jfx/pull/472), "JDK-8167148: Add native rendering support by supporting WritableImages backed by NIO ByteBuffers."

## Licenses

The content of this project is licensed under the [GNU General Public License v3.0](https://choosealicense.com/licenses/gpl-3.0/) except as follows:

* The file [duke-waving.gif](src/duke-waving.gif) is licensed under the [Creative Commons Attribution-ShareAlike 4.0 International License](https://choosealicense.com/licenses/cc-by-sa-4.0/).
* The file [Renoir_by_Bazille.jpg](src/Renoir_by_Bazille.jpg) is in the public domain and available for download from [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Renoir_by_Bazille.jpg).
* The file [PNG_transparency_demonstration_1.png](src/PNG_transparency_demonstration_1.png), by [Ed g2s](https://commons.wikimedia.org/wiki/User:Ed_g2s), is licensed under the [Attribution-ShareAlike 3.0 Unported](https://creativecommons.org/licenses/by-sa/3.0/) license and available for download from [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:PNG_transparency_demonstration_1.png).

## Building

This repository is a project of the [Apache NetBeans IDE](https://netbeans.apache.org/) with the following settings:

* the Java platform set to the default (JDK 12),
* the source and binary format set to JDK 12, and
* references to the JavaFX SDK built from the [pull request branch](https://github.com/javafxports/openjdk-jfx/pull/472).

## Running

### Animator

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

### Viewer

Run the Viewer application with a command like the following:

```ShellSession
$ $HOME/opt/jdk-12.0.1+12/bin/java --add-modules=javafx.graphics \
    --module-path=$HOME/lib/javafx-sdk-13-dev/lib Viewer
```

Change the source code to select the method used for converting the AWT image to a JavaFX image:

```Java
//        oldDraw();
//        oldCopy();
//        newDraw();
        newCopy();
        view.setImage(jfxImage);
```

### Tester

Run the Tester application with a command like the following:

```ShellSession
$ $HOME/opt/jdk-12.0.1+12/bin/java --add-modules=javafx.graphics \
    --module-path=$HOME/lib/javafx-sdk-13-dev/lib Tester
```

Click the window to cycle between the various methods for converting the AWT image to a JavaFX image.
