// IT driver: runs TWO maven invocations and asserts the second one is clean.
//
// 1. First mvn package — full build, RemovableService is part of the bean graph.
// 2. Delete src/main/java/com/example/RemovableService.java — simulates the user deleting
//    the bean between builds.
// 3. Second mvn package — must succeed AND the resulting jar must NOT contain
//    RemovableService.class. If the reconciler is broken, the old .class lingers in
//    target/classes, gets compiled into the jar, and this assertion catches it.

def projectDir = new File(basedir)
def srcFile = new File(projectDir, "src/main/java/com/example/RemovableService.java")
def targetJar = new File(projectDir, "target/staleness-app-1.0.jar")

// First build — produces a clean jar with both services.
def firstBuild = new ProcessBuilder("mvn", "-q", "-DskipTests", "package")
        .directory(projectDir)
        .redirectErrorStream(true)
        .start()
def firstOut = firstBuild.inputStream.text
if (firstBuild.waitFor() != 0) {
    throw new AssertionError("first build failed:\n" + firstOut)
}
if (!targetJar.exists()) {
    throw new AssertionError("first jar missing: " + targetJar)
}

// Mutate sources: delete the bean. Build the second time.
if (!srcFile.delete()) {
    throw new AssertionError("could not delete " + srcFile)
}

def secondBuild = new ProcessBuilder("mvn", "-q", "-DskipTests", "package")
        .directory(projectDir)
        .redirectErrorStream(true)
        .start()
def secondOut = secondBuild.inputStream.text
if (secondBuild.waitFor() != 0) {
    throw new AssertionError("second build failed after bean deletion:\n" + secondOut)
}

// Unzip the second jar and assert RemovableService.class is NOT inside.
def processJar = ["unzip", "-l", targetJar.absolutePath].execute()
def listing = processJar.inputStream.text
processJar.waitFor()
if (listing.contains("RemovableService")) {
    throw new AssertionError("RemovableService.class survived in jar after deletion:\n" + listing)
}

println "staleness verified: RemovableService.class removed from jar after second build"
