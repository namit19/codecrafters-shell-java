if (command.equals("cd")) {
    if (parts.length < 2) {
        continue;
    }

    File newDir;

    if (new File(parts[1]).isAbsolute()) {
        newDir = new File(parts[1]);
    } else {
        newDir = new File(currentDirectory, parts[1]);
    }

    newDir = newDir.getCanonicalFile();

    if (newDir.exists() && newDir.isDirectory()) {
        currentDirectory = newDir;
    } else {
        System.out.println("cd: " + parts[1] + ": No such file or directory");
    }

    continue;
}