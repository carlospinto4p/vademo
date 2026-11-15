# Release builds are not minified yet (isMinifyEnabled = false); these rules
# are what minification needs when it is switched on.

# JGit fills its message bundles (JGitText and the like) by reflection on
# their fields (`TranslationBundle` calls `getFields`), so all of it is kept
# whole. Keeping only the classes that look reflective would break the rarely
# run paths, such as the text of an error, first. Checked on 2026-11-15 by
# running `GitOnDeviceTest` on a minified build: clone, edit and push pass.
-keep class org.eclipse.jgit.** { *; }

# Desktop Java APIs JGit refers to and Android does not have: JMX monitoring
# of its caches, Kerberos sign-in over HTTP, and slf4j's logger binding. The
# app never reaches that code (it runs today without these classes), but R8
# stops on any missing class unless told not to.
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
