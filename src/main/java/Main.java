private static List<String> parseCommand(String input) {
    List<String> args = new ArrayList<>();
    StringBuilder current = new StringBuilder();

    boolean inSingleQuotes = false;

    for (int i = 0; i < input.length(); i++) {
        char ch = input.charAt(i);

        if (ch == '\'') {
            inSingleQuotes = !inSingleQuotes;
            continue;
        }

        if (Character.isWhitespace(ch) && !inSingleQuotes) {
            if (current.length() > 0) {
                args.add(current.toString());
                current.setLength(0);
            }
        } else {
            current.append(ch);
        }
    }

    if (current.length() > 0) {
        args.add(current.toString());
    }

    return args;
}